package com.bitespeed.identity.service;

import com.bitespeed.identity.dto.ContactDto;
import com.bitespeed.identity.model.Contact;
import com.bitespeed.identity.model.Contact.LinkPrecedence;
import com.bitespeed.identity.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;

    @Transactional
    public ContactDto.IdentifyResponse identify(String email, String phoneNumber) {

        List<Contact> matchingContacts = findMatchingContacts(email, phoneNumber);

        if (matchingContacts.isEmpty()) {
            Contact newContact = createContact(email, phoneNumber, null, LinkPrecedence.primary);
            return buildResponse(newContact, Collections.emptyList());
        }

        Set<Long> primaryIds = resolvePrimaryIds(matchingContacts);
        List<Contact> allContacts = loadAllContactsForPrimaries(primaryIds);

        if (primaryIds.size() > 1) {
            allContacts = mergeClusters(primaryIds, allContacts);
        }

        Contact primary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.primary)
                .min(Comparator.comparing(Contact::getCreatedAt))
                .orElseThrow();

        boolean hasNewInfo = isNewInformation(email, phoneNumber, allContacts);
        if (hasNewInfo) {
            Contact secondary = createContact(email, phoneNumber, primary.getId(), LinkPrecedence.secondary);
            allContacts.add(secondary);
        }

        List<Contact> secondaries = allContacts.stream()
                .filter(c -> !c.getId().equals(primary.getId()))
                .collect(Collectors.toList());

        return buildResponse(primary, secondaries);
    }

    private List<Contact> findMatchingContacts(String email, String phoneNumber) {
        if (email != null && phoneNumber != null)
            return contactRepository.findByEmailOrPhoneNumber(email, phoneNumber);
        else if (email != null)
            return contactRepository.findByEmail(email);
        else if (phoneNumber != null)
            return contactRepository.findByPhoneNumber(phoneNumber);
        return Collections.emptyList();
    }

    private Set<Long> resolvePrimaryIds(List<Contact> contacts) {
        Set<Long> ids = new HashSet<>();
        for (Contact c : contacts) {
            if (c.getLinkPrecedence() == LinkPrecedence.primary)
                ids.add(c.getId());
            else
                ids.add(c.getLinkedId());
        }
        return ids;
    }

    private List<Contact> loadAllContactsForPrimaries(Set<Long> primaryIds) {
        List<Contact> all = new ArrayList<>();
        for (Long pid : primaryIds)
            all.addAll(contactRepository.findAllByPrimaryId(pid));
        return all;
    }

    private List<Contact> mergeClusters(Set<Long> primaryIds, List<Contact> allContacts) {
        List<Contact> primaries = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.primary
                        && primaryIds.contains(c.getId()))
                .sorted(Comparator.comparing(Contact::getCreatedAt))
                .collect(Collectors.toList());

        Contact winner = primaries.get(0);

        for (int i = 1; i < primaries.size(); i++) {
            Contact loser = primaries.get(i);
            loser.setLinkPrecedence(LinkPrecedence.secondary);
            loser.setLinkedId(winner.getId());
            contactRepository.save(loser);

            List<Contact> loserChildren = contactRepository.findByLinkedId(loser.getId());
            for (Contact child : loserChildren) {
                child.setLinkedId(winner.getId());
                contactRepository.save(child);
            }
        }

        return loadAllContactsForPrimaries(Set.of(winner.getId()));
    }

    private boolean isNewInformation(String email, String phoneNumber, List<Contact> existing) {
        boolean emailKnown = email == null || existing.stream().anyMatch(c -> email.equals(c.getEmail()));
        boolean phoneKnown = phoneNumber == null || existing.stream().anyMatch(c -> phoneNumber.equals(c.getPhoneNumber()));
        return !emailKnown || !phoneKnown;
    }

    private Contact createContact(String email, String phoneNumber, Long linkedId, LinkPrecedence precedence) {
        Contact c = Contact.builder()
                .email(email)
                .phoneNumber(phoneNumber)
                .linkedId(linkedId)
                .linkPrecedence(precedence)
                .build();
        return contactRepository.save(c);
    }

    private ContactDto.IdentifyResponse buildResponse(Contact primary, List<Contact> secondaries) {
        List<String> emails = new ArrayList<>();
        if (primary.getEmail() != null) emails.add(primary.getEmail());
        secondaries.stream().map(Contact::getEmail)
                .filter(Objects::nonNull).filter(e -> !emails.contains(e)).forEach(emails::add);

        List<String> phones = new ArrayList<>();
        if (primary.getPhoneNumber() != null) phones.add(primary.getPhoneNumber());
        secondaries.stream().map(Contact::getPhoneNumber)
                .filter(Objects::nonNull).filter(p -> !phones.contains(p)).forEach(phones::add);

        List<Long> secondaryIds = secondaries.stream().map(Contact::getId).collect(Collectors.toList());

        return ContactDto.IdentifyResponse.builder()
                .contact(ContactDto.ContactPayload.builder()
                        .primaryContatctId(primary.getId())
                        .emails(emails)
                        .phoneNumbers(phones)
                        .secondaryContactIds(secondaryIds)
                        .build())
                .build();
    }
}