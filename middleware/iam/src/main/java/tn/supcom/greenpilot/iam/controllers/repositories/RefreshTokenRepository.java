package tn.supcom.greenpilot.iam.controllers.repositories;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import tn.supcom.greenpilot.iam.entities.RefreshToken;

@Repository
public interface RefreshTokenRepository extends BasicRepository<RefreshToken, String> {
}
