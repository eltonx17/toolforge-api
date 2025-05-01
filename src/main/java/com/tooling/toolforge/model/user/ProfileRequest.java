package com.tooling.toolforge.model.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileRequest {
    // Getters and Setters (or use Lombok @Data)
    private String uid;
    private String email;
    private String displayName;
    private String photoURL;
    private String emailVerified;
    private String providerId;

}
