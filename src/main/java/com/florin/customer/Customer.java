package com.florin.customer;

import com.florin.business.Business;
import com.florin.common.BaseEntity;
import com.florin.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pkid", nullable = false, updatable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_pkid", updatable = false)
    private User createdBy;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "account_no", length = 30)
    private String accountNo;

    @Column(name = "vat_tin_no", length = 50)
    private String vatTinNo;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;
}
