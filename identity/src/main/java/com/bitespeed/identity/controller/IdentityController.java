package com.bitespeed.identity.controller;

import com.bitespeed.identity.dto.ContactDto;
import com.bitespeed.identity.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class IdentityController {

    private final ContactService contactService;

    @PostMapping("/identify")
    public ResponseEntity<ContactDto.IdentifyResponse> identify(
            @RequestBody ContactDto.IdentifyRequest request) {

        if (request.getEmail() == null && request.getPhoneNumber() == null) {
            return ResponseEntity.badRequest().build();
        }

        ContactDto.IdentifyResponse response = contactService.identify(
                request.getEmail(),
                request.getPhoneNumber()
        );

        return ResponseEntity.ok(response);
    }
}