package com.bitespeed.identity.repository;

import com.bitespeed.identity.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @Query("SELECT c FROM Contact c WHERE c.deletedAt IS NULL AND (c.email = :email OR c.phoneNumber = :phoneNumber)")
    List<Contact> findByEmailOrPhoneNumber(@Param("email") String email, @Param("phoneNumber") String phoneNumber);

    @Query("SELECT c FROM Contact c WHERE c.deletedAt IS NULL AND c.email = :email")
    List<Contact> findByEmail(@Param("email") String email);

    @Query("SELECT c FROM Contact c WHERE c.deletedAt IS NULL AND c.phoneNumber = :phoneNumber")
    List<Contact> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("SELECT c FROM Contact c WHERE c.deletedAt IS NULL AND (c.id = :primaryId OR c.linkedId = :primaryId)")
    List<Contact> findAllByPrimaryId(@Param("primaryId") Long primaryId);

    @Query("SELECT c FROM Contact c WHERE c.deletedAt IS NULL AND c.linkedId = :linkedId")
    List<Contact> findByLinkedId(@Param("linkedId") Long linkedId);
}