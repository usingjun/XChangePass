# Project Context

## 프로젝트 개요

XChangePass는 여행 환전과 송금 흐름을 가정한 백엔드 프로젝트다.

이 프로젝트는 실제 운영 서비스가 아니라 개발 및 검증 프로젝트다.
따라서 문서, 주석, 포트폴리오 문장 작성 시 실제 운영 경험처럼 표현하지 않는다.

## 핵심 관심사

XChangePass에서 가장 중요한 관심사는 단순 기능 구현이 아니라 금융성 데이터의 안정성이다.

주요 관심사는 다음과 같다.

* 트랜잭션 정합성 Transaction Consistency
* 지갑 잔액 정합성 Wallet Balance Integrity
* 거래 원장 정합성 Ledger Consistency
* 동시성 제어 Concurrency Control
* 멱등성 Idempotency
* 장애 거래 복구 Recovery
* 이상거래 탐지 실패 시 Fail-Closed 처리
* 대용량 거래내역 조회 성능

## 기술 스택

프로젝트의 실제 코드와 설정을 우선 기준으로 삼는다.

주요 백엔드 기술은 다음과 같다.

* Java
* Spring Boot
* PostgreSQL
* Redis
* JPA
* QueryDSL
* Spring Security
* Spring Batch
* Resilience4j
* WebFlux/WebSocket
* AWS KMS
* Docker
* Testcontainers
* k6

단, 실제 프로젝트에 없는 기술을 임의로 도입하지 않는다.

## 작업 원칙

* 기존 코드 구조를 먼저 파악한다.
* 현재 프로젝트의 패키지 구조와 네이밍을 따른다.
* 최소 변경으로 문제를 해결한다.
* 핵심 비즈니스 로직 변경 시 테스트를 추가한다.
* 정합성, 동시성, 복구 관련 작업은 검증 기준을 명확히 남긴다.
* 성능 개선 작업은 가능하면 전후 수치를 남긴다.
* 운영 경험처럼 과장하지 않는다.

## 포트폴리오 표현 주의

XChangePass는 운영 프로젝트가 아니다.

따라서 아래 표현은 사용하지 않는다.

* 운영 중 발생한 장애
* 운영 환경에서 개선
* 실제 사용자 트래픽 대응
* 운영자 대응 경험

대신 아래 표현을 사용한다.

* 개발 및 검증 과정에서 확인
* 동시성 테스트를 통해 검증
* 장애 상황을 가정한 복구 구조 설계
* 대용량 데이터를 구성해 조회 성능 검증
