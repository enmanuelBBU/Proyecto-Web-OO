# Spec Delta: Perfil (User Profile)

## MODIFIED Requirements

### Requirement: A user profile SHALL carry its own uid when read back

`UserProfileService.get` and `UserProfileService.list` SHALL populate the
returned `UserProfile`'s `uid` field with the Firestore document id, so
callers that operate on a specific user (such as the admin user list) can
identify which document a given profile came from.

#### Scenario: Reading a profile includes its uid

- **GIVEN** a stored profile at `users/abc123`
- **WHEN** the service reads it back (directly, or as part of a list)
- **THEN** the returned `UserProfile.uid` SHALL equal `"abc123"`
