package dev.portfolio.finance.controller;

import dev.portfolio.finance.dto.auth.UserResponse;
import org.springframework.web.multipart.MultipartException;
import dev.portfolio.finance.service.AccountPhotoService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/account/photo")
public class AccountPhotoController {
    private final AccountPhotoService photos;

    public AccountPhotoController(AccountPhotoService photos) { this.photos = photos; }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse upload(Authentication authentication, MultipartHttpServletRequest request) {
        var files = request.getMultiFileMap();
        if (!request.getParameterMap().isEmpty() || files.size() != 1
                || !files.containsKey("photo") || files.get("photo").size() != 1) {
            throw new MultipartException("Invalid profile photo multipart request");
        }
        return photos.upload(authentication.getName(), files.getFirst("photo"));
    }

    @DeleteMapping
    public UserResponse remove(Authentication authentication) {
        return photos.remove(authentication.getName());
    }
}
