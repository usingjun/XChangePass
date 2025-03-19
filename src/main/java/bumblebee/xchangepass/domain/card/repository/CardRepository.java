package bumblebee.xchangepass.domain.card.repository;

import bumblebee.xchangepass.domain.card.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    @Query(value = "SELECT EXISTS(SELECT 1 FROM Card WHERE cardNumber = :cardNumber)")
    Boolean existsByCardNumber(@Param("cardNumber") String cardNumber);

}
