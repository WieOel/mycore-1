/*
 * 
 * $Revision$ $Date$
 *
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */

package org.mycore.frontend.fileupload;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This class is used by applets to communicate with the corresponding
 * MCRUploadServlet servlet to execute tasks on the server. For example, the
 * applet may invoke the createDocument method and pass it a document instance
 * to create this document in the persistent datastore on the server side. The
 * MCRUploadCommunicator does some marshalling etc. and sends the request to the
 * MCRUploadServlet servlet that does the job.
 * 
 * @author Frank Lützenkirchen
 * @author Harald Richter
 * @author Jens Kupferschmidt
 * @author Thomas Scheffler (yagee)
 * @version $Revision$ $Date$
 * @see org.mycore.frontend.fileupload.MCRUploadServlet
 */
public class MCRUploadCommunicator {
    protected String url;

    protected String uid;

    protected MCRUploadProgressMonitor upm;

    protected MCRUploadApplet applet;

    protected final static int bufferSize = 65536; // 64 KByte

    public MCRUploadCommunicator(String url, String uploadId, MCRUploadApplet applet) {
        this.url = url;
        uid = uploadId;
        this.applet = applet;
    }

    public void uploadFiles(File[] selectedFiles) {
        try {
            Vector[] list = listFiles(selectedFiles);
            upm = new MCRUploadProgressMonitor(list[0].size(), countTotalBytes(list[0]), applet);
            startUploadSession(list[0].size());
            loadFiles(list);
            if (upm.isCanceled()) {
                System.out.println("Upload is canceled by user.");
                cancelUploadSession();
            } else {
                endUploadSession();
                upm.finish();
            }
        } catch (Exception ex) {
            String msg = ex.getClass().getName() + ": " + ex.getLocalizedMessage();

            if (ex instanceof MCRUploadException) {
                MCRUploadException uex = (MCRUploadException) ex;
                msg = "Fehlermeldung des Servers: " + uex.getServerSideClassName() + ": " + uex.getMessage();
            }

            System.out.println("Exception caught: " + msg);
            ex.printStackTrace(System.out);

            if (upm != null) {
                upm.cancel(ex);
            } else {
                MCRUploadProgressMonitor.reportException(ex);
            }
        }
    }

    protected long countTotalBytes(Vector files) {
        long total = 0;

        for (Object file : files) {
            total += ((File) file).length();
        }

        return total;
    }

    public void loadFiles(Vector[] list) throws Exception {
        if (list[0].size() == 0) {
            throw new IllegalArgumentException("Sie haben keine Dateien ausgewählt!");
        }

        for (int i = 0; i < list[0].size(); i++) {
            if (upm.isCanceled()) {
                return;
            }

            File file = (File) list[0].get(i);
            String path = (String) list[1].get(i);
            upm.startFile(file.getName(), file.length());

            uploadFile(path, file);

            if (!upm.isCanceled()) {
                upm.endFile();
            }
        }
    }

    public void uploadFile(String path, File file) throws Exception {
        System.out.println("--- Starting filetransfer ---");

        String md5 = buildMD5String(file);
        System.out.println("MD5 checksum is " + md5);

        if (upm.isCanceled()) {
            return;
        }

        // TODO: Refactor method names in communication
        Hashtable request = new Hashtable();
        request.put("md5", md5);
        request.put("method", "uploadFile");
        request.put("path", path);
        request.put("length", String.valueOf(file.length()));

        System.out.println("Sending filename to server: " + path);

        String reply = (String) send(request);
        System.out.println("Received reply from server.");

        if ("skip file".equals(reply)) {
            System.out.println("File skipped.");
            return;
        }

        StringTokenizer st = new StringTokenizer(reply, ":");
        String host = st.nextToken();
        int port = Integer.parseInt(st.nextToken());
        System.out.println("Server says we should connect to " + host + ":" + port);

        System.out.println("Trying to create client socket...");

        if (upm.isCanceled()) {
            return;
        }

        Socket socket = new Socket(host, port);
        socket.setReceiveBufferSize(Math.max(socket.getReceiveBufferSize(), bufferSize));
        socket.setSendBufferSize(Math.max(socket.getSendBufferSize(), bufferSize));

        System.out.println("Socket created, connected to server.");
        System.out.println("Socket send buffer size is " + socket.getSendBufferSize());

        ZipOutputStream zos = new ZipOutputStream(socket.getOutputStream());
        DataInputStream din = new DataInputStream(socket.getInputStream());

        // Large files like video already are compressed somehow
        zos.setLevel(Deflater.NO_COMPRESSION);

        ZipEntry ze = new ZipEntry(java.net.URLEncoder.encode(path, "UTF-8"));
        StringBuilder extra = new StringBuilder();
        extra.append(md5).append(" ").append(file.length()).append(" ").append(uid);
        ze.setExtra(extra.toString().getBytes("UTF-8"));
        zos.putNextEntry(ze);

        int num = 0;
        byte[] buffer = new byte[bufferSize];

        System.out.println("Starting to send file content...");

        InputStream source = new BufferedInputStream(new FileInputStream(file), buffer.length);

        long lastPing = System.currentTimeMillis();
        while ((num = source.read(buffer)) != -1) {
            if (upm.isCanceled()) {
                break;
            }
            zos.write(buffer, 0, num);
            upm.progressFile(num);

            // Send a "ping" to MCRUploadServlet so that server keeps HTTP Session alive
            if (System.currentTimeMillis() - lastPing > 10000) {
                lastPing = System.currentTimeMillis();
                Hashtable <String, String> ping = new Hashtable<String, String>();
                ping.put("method", "ping");
                System.out.println("Sending ping to servlet...");
                String pong = (String) send(ping);
                System.out.println("Server responded with " + pong);
            }
        }

        zos.closeEntry();
        zos.flush();
        System.out.println("Releasing file: " + file);
        source.close();
        System.out.println("Finished sending file content.");

        socket.close();

        if (upm.isCanceled()) {
            return;
        }

        System.out.println("Socket closed, file transfer successfully completed.");
    }

    /**
     * Creates a list of all files in the given directories
     * 
     * @param selectedFiles
     *            list of selected files or directories from filechooser
     */
    protected Vector[] listFiles(File[] selectedFiles) throws Exception {
        Vector[] list = new Vector[2];
        list[0] = new Vector();
        list[1] = new Vector();

        if (null == selectedFiles || 0 == selectedFiles.length) {
            return list;
        }

        for (File selectedFile : selectedFiles) {
            File f = selectedFile;

            if (!f.exists()) {
                throw new FileNotFoundException("Datei oder Verzeichnis " + f.getPath() + " nicht gefunden!");
            }

            if (!f.canRead()) {
                throw new IOException("Datei oder Verzeichnis " + f.getPath() + " nicht lesbar!");
            }

            if (f.isFile()) {
                list[0].addElement(f);
                list[1].addElement(f.getName());
            } else {
                Stack dirStack = new Stack();
                Stack baseStack = new Stack();

                dirStack.push(f);
                baseStack.push(f.getName() + "/");

                while (!dirStack.empty()) {
                    File dir = (File) dirStack.pop();
                    String base = (String) baseStack.pop();

                    String[] files = dir.list();

                    for (String file : files) {
                        f = new File(dir, file);

                        if (f.isFile()) {
                            list[0].addElement(f);
                            list[1].addElement(base + file);
                        } else {
                            dirStack.push(f);
                            baseStack.push(base + file + "/");
                        }
                    }
                }
            }
        }

        return list;
    }

    protected void startUploadSession(int numFiles) throws IOException, MCRUploadException {
        Hashtable request = new Hashtable();
        request.put("method", "startUploadSession");
        request.put("numFiles", String.valueOf(numFiles));
        send(request);
    }

    protected void endUploadSession() throws IOException, MCRUploadException {
        Hashtable request = new Hashtable();
        request.put("method", "endUploadSession");
        send(request);
    }

    protected void cancelUploadSession() throws IOException, MCRUploadException {
        Hashtable request = new Hashtable();
        request.put("method", "cancelUploadSession");
        send(request);
    }

    protected Object send(Hashtable parameters) throws IOException, MCRUploadException {
        parameters.put("uploadId", uid);
        Hashtable response = getResponse(doPost(parameters));
        return response.get("return");
    }

    protected InputStream doPost(Hashtable parameters) throws IOException {
        String data = encodeParameters(parameters);
        String mime = "application/x-www-form-urlencoded";

        URLConnection connection = null;

        try {
            connection = new URL(url).openConnection();
        } catch (MalformedURLException ignored) {
        } // will never happen if base URL is ok

        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);
        connection.setRequestProperty("Content-type", mime);
        connection.setRequestProperty("Content-length", String.valueOf(data.length()));

        DataOutputStream out = new DataOutputStream(connection.getOutputStream());
        out.writeBytes(data);
        out.flush();
        out.close();

        return connection.getInputStream();
    }

    protected String encodeParameters(Hashtable parameters) {
        StringBuilder data = new StringBuilder();
        Enumeration e = parameters.keys();

        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            String value = (String) parameters.get(name);

            try {
                data.append(URLEncoder.encode(name, "UTF-8")).append("=").append(URLEncoder.encode(value, "UTF-8")).append("&");
            } catch (UnsupportedEncodingException ex) {
                System.out.println(ex.getClass().getName());
                System.out.println(ex.getMessage());
                throw new RuntimeException("Could not encode parameters");
            }
        }

        data.setLength(data.length() - 1);

        return data.toString();
    }

    protected Hashtable getResponse(InputStream is) throws IOException, MCRUploadException {
        DataInputStream dis = new DataInputStream(is);
        String mime = dis.readUTF();
        byte[] dummy = new byte[0];

        Hashtable response = new Hashtable();

        while (dis.read(dummy, 0, 0) != -1) {
            String key = dis.readUTF();
            String clname = dis.readUTF();
            Object value = null;

            if (clname.equals(String.class.getName())) {
                value = dis.readUTF();
            } else if (clname.equals(Integer.class.getName())) {
                value = dis.readInt();
            } else {
                value = dis.readUTF();
            }

            response.put(key, value);
        }

        if (mime.equals("upload/exception")) {
            String clname = (String) response.get("clname");
            String message = (String) response.get("message");
            String strace = (String) response.get("strace");
            throw new MCRUploadException(clname, message, strace);
        }

        return response;
    }

    /** Calculates the MD5 checksum of the given local file * */
    protected String buildMD5String(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");

        InputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis, bufferSize);
        DigestInputStream in = new DigestInputStream(bis, digest);

        byte[] buffer = new byte[bufferSize];

        while (in.read(buffer, 0, buffer.length) != -1) {
        }

        in.close();

        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            String sValue = "0" + Integer.toHexString(b);
            sb.append(sValue.substring(sValue.length() - 2));
        }

        return sb.toString();
    }
}