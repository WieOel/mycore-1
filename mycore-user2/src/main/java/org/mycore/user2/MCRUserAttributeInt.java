package org.mycore.user2;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;


@Entity
@Table(name = "MCRUserAttr", indexes = {
        @Index(name = "MCRUserAttributes", columnList = "name, value"),
        @Index(name = "MCRUserValues", columnList = "value")})
public class MCRUserAttributeInt {

    /** The ID of the attribute*/
    int id;

    String name;

    String value;

    public MCRUserAttributeInt() {

    }

    public MCRUserAttributeInt(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * @return the id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    int getId() {
        return id;
    }

    /**
     * @param id the ID to set
     */
    void setId(int id) {
        this.id = id;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    @Column(name = "value", nullable = false)
    public String getValue() {
        return value;
    }

    void setValue(String value) {
        this.value = value;
    }

}
