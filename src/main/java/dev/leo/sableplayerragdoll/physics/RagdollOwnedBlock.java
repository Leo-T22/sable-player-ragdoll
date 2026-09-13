package dev.leo.sableplayerragdoll.physics;

import java.util.UUID;

public interface RagdollOwnedBlock {
    RagdollBlockIdentity ragdollIdentity();
    void setRagdollIdentity(UUID owner, UUID limb, String kind);
    void markSevered();
}
