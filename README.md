# OntoSov: Personal Data Sovereignty Framework

A virtual data federation framework enabling individuals to discover, govern, and monitor their personal data across multiple organizational databases without requiring data migration.

**PhD Research Project** | University of York | 2023-2026

---

## Overview

OntoSoV (Ontology-based Sovereignty) addresses the challenge of distributed personal data by implementing a virtual knowledge graph layer over heterogeneous databases. The system enables fine-grained policy control and blockchain-verified audit logging while allowing data to remain in organizational databases.

### Core Innovation

- **Virtual Federation**: Ontology-Based Data Access (OBDA) with Schema.org mappings
- **Policy Control**: W3C ODRL-based governance with AI-specific extensions  
- **Transparency**: Blockchain-verified immutable audit logs
- **Dual Privacy**: Property-level and entity-level access control

---

## Key Features

- **Data Federation**: SPARQL-based querying across PostgreSQL, MySQL, and other relational databases
- **Fine-Grained Policies**: Control access permissions (read, use, share, aggregate, modify, AI training)
- **Policy Constraints**: Temporal limits, purpose restrictions, and algorithm-specific requirements
- **Multiple Policy Evaluation**: "Most restrictive wins" conflict resolution
- **Blockchain Audit**: Ethereum-based immutable access logs
- **User Interfaces**: React-based dashboards for subjects and data controllers

---

## Architecture
```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Subject   │────▶│  Spring Boot │────▶│ Controllers │
│  Interface  │     │   Backend    │     │  Databases  │
└─────────────┘     │              │     └─────────────┘
                    │  - PDP       │
┌─────────────┐     │  - OBDA      │     ┌─────────────┐
│ Controller  │────▶│  - ODRL      │────▶│  Blockchain │
│  Interface  │     │  - Web3      │     │   (ETH)     │
└─────────────┘     └──────────────┘     └─────────────┘
```

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 3.x (Java 17) |
| Frontend | React 18 + Material-UI |
| Data Federation | Ontop v4 (SPARQL-to-SQL) |
| Policy Storage | Apache Jena TDB2 (RDF) |
| Blockchain | Web3j + Ethereum |
| Databases | PostgreSQL, MySQL |

---

## Quick Start

### Prerequisites

- Java 17+, Maven
- Node.js 16+, npm
- PostgreSQL (system database)
- Ganache (local blockchain)

### Installation

**Backend:**
```bash
cd backend
mvn clean install
mvn spring-boot:run  # Runs on http://localhost:8080
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev  # Runs on http://localhost:5173
```

**Blockchain:**
```bash
cd blockchain
npm install
ganache  # Start local blockchain
npx hardhat run scripts/deploy.js --network localhost
```

### Configuration

Edit `backend/src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ontosov
spring.datasource.username=your_username
spring.datasource.password=your_password
blockchain.network=http://localhost:8545
```

---

## Usage

### For Data Subjects

1. Register with tax ID (unique identifier)
2. View federated personal data across all connected controllers
3. Create policy groups (or use privacy tier templates)
4. Assign policies to data elements (property or entity level)
5. Monitor access history via blockchain-verified audit logs

### For Data Controllers

1. Register as controller
2. Connect database and map schema to Schema.org vocabulary
3. Use Policy Checker to verify access permissions before data requests
4. View access history and compliance status

---

## Project Structure
```
ontosov/
├── backend/
│   ├── src/main/java/com/ontosov/
│   │   ├── controllers/       # REST API endpoints
│   │   ├── services/          # Business logic (PDP, OBDA, ODRL)
│   │   ├── models/            # JPA entities
│   │   └── dto/               # Data transfer objects
│   └── src/main/resources/
│       ├── application.properties
│       └── ontop/             # OBDA mappings
├── frontend/
│   └── src/
│       ├── pages/             # Subject/Controller dashboards
│       └── components/        # UI components
├── blockchain/
│   ├── contracts/             # Solidity smart contracts
│   └── scripts/               # Deployment scripts
└── docs/                      # Additional documentation
```

---

## Research Context

This framework is part of PhD research at the University of York investigating practical implementations of personal data sovereignty in distributed systems.

### Publications

Find a list of publications related to this research under my Google Scholar [account](https://scholar.google.com/citations?user=BxKRTwQAAAAJ&hl=en&oi=ao) 

---

## Compliance

The framework is designed to support compliance with:

- GDPR (General Data Protection Regulation)
- EU Data Governance Act
- EU Data Act
- EU AI Act

---

## License

MIT License - see [LICENSE](LICENSE) file for details.

---

## Contact

**Vijon Baraku**  
PhD Researcher, University of York  
LinkedIn: [linkedin.com/in/vijon-baraku](https://www.linkedin.com/in/vijon-baraku-3aa660232/)
