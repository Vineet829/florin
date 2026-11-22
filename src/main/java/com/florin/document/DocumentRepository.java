package com.florin.document;

import com.florin.customer.Customer;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"customer"})
    List<Document> findByPkidIn(Collection<Long> pkids);

    @EntityGraph(attributePaths = {"billingItems"})
    List<Document> findByBusinessPkidOrderByCreatedAtDesc(Long businessPkid);

    @EntityGraph(attributePaths = {"customer"})
    Page<Document> findByBusinessPkid(Long businessPkid, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "billingItems"})
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findByExternalId(@Param("externalId") UUID externalId);

    @EntityGraph(attributePaths = {"payments"})
    @Query("""
            select d from Document d
            where d.customer.pkid = :customerPkid and d.documentType = :type
              and d.issuedAt is not null and d.voidedAt is null
            order by d.issueDate, d.createdAt, d.pkid
            """)
    List<Document> findForStatement(@Param("customerPkid") Long customerPkid, @Param("type") DocumentType type);

    List<Document> findBySourceOrderByCreatedAtAscPkidAsc(Document source);

    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.documentType in :types
              and d.issuedAt is not null and d.voidedAt is null
              and d.issueDate between :from and :to
              and d.taxRegime = com.florin.tax.TaxRegime.GST_INDIA and d.supplyType is not null
            order by d.issueDate, d.documentNumber, d.pkid
            """)
    List<Document> findGstDatedIn(
            @Param("businessPkid") Long businessPkid,
            @Param("types") Collection<DocumentType> types,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(d) from Document d
            where d.business.pkid = :businessPkid and d.documentType in :types
              and d.issuedAt is not null and d.voidedAt is null
              and d.issueDate between :from and :to
              and (d.taxRegime <> com.florin.tax.TaxRegime.GST_INDIA or d.supplyType is null)
            """)
    long countOutsideGstDatedIn(
            @Param("businessPkid") Long businessPkid,
            @Param("types") Collection<DocumentType> types,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select d.pkid from Document d
            where d.documentType = :invoice and d.issuedAt is not null and d.voidedAt is null
              and d.status <> :paid and d.total > 0
              and d.dueDate < :before and d.overdueAlertedAt is null
            order by d.pkid
            """)
    List<Long> findUnalertedOverdue(
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from Document d
            where d.pkid = :pkid
              and d.documentType = :invoice and d.issuedAt is not null and d.voidedAt is null
              and d.status <> :paid and d.total > 0
              and d.dueDate < :before and d.overdueAlertedAt is null
            """)
    Optional<Document> findUnalertedOverdueForUpdate(
            @Param("pkid") Long pkid,
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("before") Instant before);

    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.documentType = :invoice
              and d.issuedAt is not null and d.voidedAt is null
              and d.status <> :paid and d.total > 0 and d.dueDate is not null and d.remindersSent < :most
            """)
    List<Document> findRemindable(
            @Param("businessPkid") Long businessPkid,
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            @Param("most") int most);

    Optional<Document> findFirstBySourceAndDocumentType(Document source, DocumentType documentType);

    @Query(
            value = """
                    select d from Document d
                    where d.business.pkid = :businessPkid and d.customer.pkid = :customerPkid
                      and d.issuedAt is not null
                    order by d.issueDate desc, d.pkid desc
                    """,
            countQuery = """
                    select count(d) from Document d
                    where d.business.pkid = :businessPkid and d.customer.pkid = :customerPkid
                      and d.issuedAt is not null
                    """)
    Page<Document> findForPortal(
            @Param("businessPkid") Long businessPkid, @Param("customerPkid") Long customerPkid, Pageable pageable);

    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.customer.pkid = :customerPkid
              and d.documentNumber = :number and d.issuedAt is not null
            """)
    Optional<Document> findInPortal(
            @Param("businessPkid") Long businessPkid,
            @Param("customerPkid") Long customerPkid,
            @Param("number") String number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.customer.pkid = :customerPkid
              and d.documentNumber = :number and d.issuedAt is not null
            """)
    Optional<Document> findForUpdateInPortal(
            @Param("businessPkid") Long businessPkid,
            @Param("customerPkid") Long customerPkid,
            @Param("number") String number);

    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.documentType = :type
              and d.issuedAt is not null and d.voidedAt is null
            order by d.issuedAt desc, d.pkid desc
            """)
    List<Document> findLatestIssued(
            @Param("businessPkid") Long businessPkid, @Param("type") DocumentType type, Pageable pageable);

    @Query("""
            select d from Document d
            where d.business.pkid = :businessPkid and d.documentType = :invoice
              and d.issuedAt is not null and d.voidedAt is null and d.status <> :paid and d.total > 0
            order by d.issuedAt desc, d.pkid desc
            """)
    List<Document> findLatestUnpaid(
            @Param("businessPkid") Long businessPkid,
            @Param("invoice") DocumentType invoice,
            @Param("paid") DocumentStatus paid,
            Pageable pageable);

    @Query("""
            select p from Payment p join p.document d
            where d.business.pkid = :businessPkid and d.voidedAt is null
            order by p.createdAt desc, p.pkid desc
            """)
    List<Payment> findLatestPayments(@Param("businessPkid") Long businessPkid, Pageable pageable);

    @Query("""
            select d.customer from Document d
            where d.business.pkid = :businessPkid and d.documentType = :invoice
              and d.issuedAt is not null and d.voidedAt is null and d.customer is not null
            order by d.issuedAt desc, d.pkid desc
            """)
    List<Customer> findLatestInvoicedCustomers(
            @Param("businessPkid") Long businessPkid, @Param("invoice") DocumentType invoice, Pageable pageable);

    boolean existsByBusinessPkidAndDocumentNumber(Long businessPkid, String documentNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :externalId")
    Optional<Document> findForUpdateByExternalId(@Param("externalId") UUID externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.pkid = :pkid")
    Optional<Document> findForUpdateByPkid(@Param("pkid") Long pkid);

    @Query(value = "select credited_pkid from documents where id = :externalId", nativeQuery = true)
    Optional<Long> findCreditedPkid(@Param("externalId") UUID externalId);
}
