package com.example.payment_api.entity;

import com.example.payment_api.enums.RoleName;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 50, updatable = false)
    private RoleName name;

    protected Role() {}

    public Role(RoleName name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    // Roles are identified by their unique name, so equality works before and after saving.
    // Uses getName() rather than the field: a Hibernate proxy only loads its data through
    // getters, so reading role.name directly on a proxy would give null.
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Role role && getName() == role.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
