package tn.supcom.greenpilot.iam.controllers.repositories;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import tn.supcom.greenpilot.iam.entities.Identity;

import java.util.Optional;

/** Jakarta Data repository: Eclipse JNoSQL generates the implementation at deployment. */
@Repository
public interface IdentityRepository extends BasicRepository<Identity, String> {
    Optional<Identity> findByUsername(String username);
}
