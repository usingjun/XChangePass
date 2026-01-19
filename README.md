# 📚 실시간 환율 프로젝트 [XCP]

<div align="center">
<img src="docs/XCP.png" alt="플로우 차트" width="50%">
</div>

## ✨ 프로젝트 개요

XChangePass 백엔드 서비스는 **실전 금융 트러블슈팅 경험**을 쌓기 위해 실시간 환율 연동, 멀티 통화 지갑, 예약 송금·알림 기능을 직접 설계·구현하는 것을 목표로 합니다.

## ✨ 기획 배경

> * **실제 금융 API 연동 과정**에서 마주치는 보안·성능 이슈를 해결해 보고,
> * **대규모 트래픽** 상황에서의 캐시 전략·트랜잭션 관리 경험을 쌓으며,
> * **운영 환경**에서의 모니터링·알림·절차를 체험하는 것이 본 프로젝트의 핵심 학습 목표입니다.
> 
> 
> 실무 수준의 백엔드 운영 역량을 확보하고자 합니다.

---

## ✨ 프로젝트 기간

* **기획 및 설계 :** 2025.02.07 ~ 2025.02.14
* **개발 :** 2025.02.15 ~ 2025.04.25

---

## ✨ 주요 기능

### 1. 실시간 환율 기반 스마트 환전
사용자가 가장 유리한 시점에 환전할 수 있도록 지연 없는 금융 데이터를 제공합니다.
- 실시간 환율 동기화: 외부 API 연동과 비동기 처리 시스템을 통해 대기 시간 없이 항상 최신 환율 정보를 제공하며, 사용자가 즉시 환전을 진행할 수 있는 환경을 구축했습니다.
- 최적의 환전 경험: 지능형 캐싱 전략을 통해 환율 변동에 민감한 금융 사용자들이 가장 유리한 타이밍에 외화를 확보할 수 있도록 돕습니다.

### 2. 다국적 화폐 지원하는 지갑 & 송금
복잡한 외화 관리와 송금을 클릭 몇 번으로 해결하는 통합 금융 경험을 제공합니다.
- 통합 자산 관리: 달러, 엔, 유로 등 다양한 국가의 화폐를 하나의 지갑에서 간편하게 관리하고, 실시간으로 화폐별 잔액을 조회할 수 있습니다.
- 끊김 없는 글로벌 송금: 어떤 통화로든 빠르고 정확하게 자금을 주고받을 수 있도록 설계되었습니다. 특히 대규모 사용자가 동시에 이용하는 상황에서도 단 1원의 오차 없이 정합성을 유지하는 견고한 거래 환경을 보장합니다.

### 3. 보안을 최우선으로 하는 카드 서비스
사용자의 민감한 금융 정보를 철저히 보호하면서도 편리한 카드 이용을 지원합니다.
- 안전한 실물 카드 발급: 금융권 수준의 암호화 기술을 적용하여 실물 카드를 안전하게 발급하고 관리합니다.
- 민감 정보 철벽 보호: 카드 번호, CVC 등 핵심 보안 데이터는 이중 암호화되어 저장됩니다. 사용자가 정보를 조회할 때는 철저한 본인 인증과 보안 검증을 거쳐 안전하게 노출되도록 설계되었습니다.

### 4. 믿을 수 있는 거래 모니터링 및 복구
어떤 상황에서도 사용자의 거래가 안전하게 완료되도록 24시간 감시 체계를 유지합니다.
- 실시간 장애 알림: 환전이나 송금 과정에서 예기치 못한 실패가 발생할 경우, 시스템이 이를 즉시 감지하여 운영진에게 알리고 사용자가 상황을 인지할 수 있도록 돕습니다.
- 철저한 데이터 보호: 시스템 장애가 발생하더라도 거래 기록이 유실되지 않도록 정교한 복구 로직이 작동하며, 모든 거래는 끝까지 추적되어 안전하게 처리됩니다.

---

## ✨ 기술 스택

<div align="left">
<img src="https://img.shields.io/badge/JAVA-FFA500?style=for-the-badge&logo=openjdk&logoColor=white">
<img src="https://img.shields.io/badge/SPRING-6DB33F?style=for-the-badge&logo=spring&logoColor=white">
<img src="https://img.shields.io/badge/SPRINGBOOT-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">





<img src="https://img.shields.io/badge/PostgreSQL-4479A1?style=for-the-badge&logo=PostgreSQL&logoColor=white">
<img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=Redis&logoColor=white">





<img src="https://img.shields.io/badge/JUnit5-25A162?style=for-the-badge&logo=JUnit5&logoColor=white">
<img src="https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=Hibernate&logoColor=white">
<img src="https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white">
<img src="https://img.shields.io/badge/grafana-%23F46800.svg?style=for-the-badge&logo=grafana&logoColor=white">
<img src="https://img.shields.io/badge/K6-%237D64FF.svg?style=for-the-badge&logo=k6&logoColor=white">
</div>

---

## 📊 시퀀스 및 플로우 다이어그램

<details>
<summary><b>💳 카드 관리 주요 흐름</b></summary>




#### 1) 실물 카드 발급 시퀀스

#### 2) 카드 상세 조회 시퀀스

</details>

<details>
<summary><b>💱 환전 플로우 및 트랜잭션</b></summary>




</details>

<details>
<summary><b>💸 거래 시스템 주요 흐름</b></summary>




</details>

<details>
<summary><b>🔐 토큰 기반 인증 흐름</b></summary>




<img width="568" alt="토큰 기반 인증 흐름 drawio" src="https://github.com/user-attachments/assets/cafb3eb3-1826-470d-9cc4-664b9046bbfe" />
</details>

<details>
<summary><b>📝 거래내역 저장 시퀀스</b></summary>




<img width="939" alt="거래내역 저장 시퀀스 다이어그램 drawio" src="https://github.com/user-attachments/assets/e12d5306-05df-45c2-9a3f-ce78116979f1" />
</details>

---

## 👀 프로젝트 화면

<details>
<summary><b>🏠 메인 화면</b></summary>
<img src="https://github.com/user-attachments/assets/90733bdb-387f-43ac-acc1-3d9bf385c7f3" width="100%" />
<img src="https://github.com/user-attachments/assets/35bcbfed-ae11-4455-899e-90189c97ffa7" width="100%" />
<img src="https://github.com/user-attachments/assets/a384358f-cb64-4929-8156-dda67231668e" width="100%" />
</details>

<details>
<summary><b>💸 거래 화면</b></summary>
<img src="https://github.com/user-attachments/assets/5f40c5a4-d11b-4107-91db-6930376f99c9" width="100%" />
</details>

<details>
<summary><b>💳 카드 화면</b></summary>
<img src="https://github.com/user-attachments/assets/c202e793-f742-4547-847b-14b55242967c" width="100%" />
</details>

<details>
<summary><b>💱 환전 화면</b></summary>
<img src="https://github.com/user-attachments/assets/8d9801d5-2519-4b63-8ea2-42e939b6ed1e" width="100%" />
</details>

---

## ✨ 개발 문서

<details>
<summary><b>📁 ERD (Entity Relationship Diagram)</b></summary>




</details>

<details>
<summary><b>📜 컨벤션 (Conventions)</b></summary>

* [팀 규칙](https://silky-toothbrush-191.notion.site/ee1575c5d056473f83d9f56f40edaa47)
* [공통 커밋 컨벤션](https://silky-toothbrush-191.notion.site/3903032f148543b685d3de474249d31f)
* [백엔드 코드 컨벤션](https://silky-toothbrush-191.notion.site/70565c77e3b34b38bb8d2d56ca7a6a54)

</details>

---

## ✨ 팀 소개


|                             BE                             |                             BE                             |                             BE                             |
|:----------------------------------------------------------:|:----------------------------------------------------------:|:----------------------------------------------------------:|
| ![](https://avatars.githubusercontent.com/u/176664628?v=4) | ![](https://avatars.githubusercontent.com/u/134962465?v=4) | ![](https://avatars.githubusercontent.com/u/97494494?v=4)  |
|                        Team Leader                         |                         Developer                          |                         Developer                          |
|             [강시영](https://github.com/Si-rauis)             |           [이시현](https://github.com/CryingPerson)           |             [이용준](https://github.com/usingjun)             |
|        카드 관리 / 유저 CRUD / <br/> 금융 정보 암호화 / Jira 연동         |                  실시간 환율 정보/<br/>동시성 제어 환전                  | 거래 시스템(송금, 충전, 출금) / 시큐리티  구성(로그인)<br/>/지갑 거래내역(장애 복구, 알림) |
