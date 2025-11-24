# OntoSov Scalability Test Report

## Executive Summary

**Test Date:** [YYYY-MM-DD]
**Test Environment:** [Development/Staging/Production]
**Tester:** Vijon Baraku

### Key Findings

| Metric | Small (10) | Medium (100) | Large (1K) | XLarge (10K) |
|--------|------------|--------------|------------|--------------|
| Query Time (ms) | - | - | - | - |
| Records Returned | - | - | - | - |
| Scaling Factor | 1.0x | - | - | - |

---

## 1. Test Environment

### 1.1 System Configuration

| Component | Specification |
|-----------|--------------|
| OS | Linux |
| Java Version | 17 |
| Spring Boot | 3.3.5 |
| Ontop Version | 5.1.0 |
| PostgreSQL | 42.3.1 (driver) |
| MySQL | 8.0.31 (driver) |

### 1.2 Database Configuration

**E-commerce Database (PostgreSQL)**
- Host: localhost:5432
- Database: ecommerce_db
- Tables: user_profiles, order_history, products, store_locations
- Controller ID: 8
- UUID: 05ae7eff-a080-412a-b448-e28d6e75a7d2

**Healthcare Database (MySQL)**
- Host: localhost:3306
- Database: health_research_center
- Tables: patients, medical_records
- Controller ID: 8
- UUID: d8e09744-178b-4870-a8d4-8597666d0428

### 1.3 Test Data Summary

| Test Subject | Tax ID | E-commerce Orders | Medical Records | Total Records |
|--------------|--------|-------------------|-----------------|---------------|
| Small | TEST0000010 | 10 | 10 | 20 |
| Medium | TEST0000100 | 100 | 100 | 200 |
| Large | TEST0001000 | 1,000 | 1,000 | 2,000 |
| XLarge | TEST0010000 | 10,000 | 10,000 | 20,000 |

---

## 2. Test Results

### 2.1 Data Volume Scalability

This test measures how query performance scales with increasing data volume.

#### Results Table

| Scale | Expected Records | Actual Records | Avg Time (ms) | Min (ms) | Max (ms) |
|-------|------------------|----------------|---------------|----------|----------|
| Small | 10 | - | - | - | - |
| Medium | 100 | - | - | - | - |
| Large | 1,000 | - | - | - | - |
| XLarge | 10,000 | - | - | - | - |

#### Scaling Analysis

```
Expected Linear Scaling: O(n)
Expected Logarithmic Scaling: O(log n)
Observed Scaling Pattern: [To be filled after tests]
```

#### Performance Graph

```
Query Time (ms)
    ^
    |
    |                              * XLarge
    |
    |                    * Large
    |
    |          * Medium
    |    * Small
    +---------------------------------> Data Volume
         10    100   1K    10K
```

### 2.2 Baseline Comparison (SPARQL vs SQL)

This test compares the overhead of SPARQL federated queries against direct SQL access.

#### Results Table

| Scale | SPARQL Time (ms) | SQL Baseline (ms) | Overhead (%) |
|-------|------------------|-------------------|--------------|
| Small | - | - | - |
| Medium | - | - | - |
| Large | - | - | - |
| XLarge | - | - | - |

#### Analysis

```
Federation Overhead Formula: ((SPARQL - SQL) / SQL) * 100%

Acceptable Overhead Threshold: < 300%
Observed Average Overhead: [To be filled]
```

### 2.3 Concurrent Query Performance

This test measures system behavior under concurrent load.

#### Results Table

| Parallel Queries | Total Time (ms) | Avg per Query (ms) | Max Query (ms) |
|------------------|-----------------|--------------------| ---------------|
| 5 | - | - | - |
| 10 | - | - | - |

#### Throughput Analysis

```
Queries per Second (5 parallel): [To be calculated]
Queries per Second (10 parallel): [To be calculated]
```

---

## 3. Observations and Analysis

### 3.1 Scaling Behavior

**Linear Scaling Indicators:**
- [ ] Query time increases proportionally with data volume
- [ ] Memory usage scales linearly
- [ ] No exponential degradation observed

**Sub-linear Scaling (Good):**
- [ ] Database indexing effective
- [ ] Query optimization working
- [ ] Caching benefits observed

**Super-linear Scaling (Concerning):**
- [ ] Potential query plan issues
- [ ] Missing indexes
- [ ] Memory pressure

### 3.2 Federation Overhead Analysis

| Component | Estimated Overhead |
|-----------|-------------------|
| SPARQL Parsing | ~5-10% |
| Ontop Translation | ~10-20% |
| Multi-DB Coordination | ~20-40% |
| Result Merging | ~5-10% |
| **Total Expected** | **40-80%** |

### 3.3 Bottleneck Identification

- [ ] CPU bound (query processing)
- [ ] I/O bound (database access)
- [ ] Network bound (multi-DB communication)
- [ ] Memory bound (result set handling)

---

## 4. Recommendations

### 4.1 Short-term Optimizations

1. **Database Indexing**
   - Ensure indexes on `tax_id` / `tax_identifier` columns
   - Add composite indexes for common query patterns

2. **Query Optimization**
   - Review SPARQL query for efficiency
   - Consider query result caching

3. **Connection Pooling**
   - Verify connection pool sizing
   - Monitor connection wait times

### 4.2 Long-term Improvements

1. **Horizontal Scaling**
   - Consider read replicas for databases
   - Implement query load balancing

2. **Caching Layer**
   - Add Redis/Memcached for frequent queries
   - Implement result set caching

3. **Query Plan Optimization**
   - Analyze Ontop-generated SQL
   - Consider materialized views for common patterns

---

## 5. Test Execution Details

### 5.1 How to Reproduce

1. **Populate Test Data:**
   ```bash
   # PostgreSQL (ecommerce_db)
   psql -h localhost -U username -d ecommerce_db -f test-data/populate_ecommerce_test_data.sql

   # MySQL (health_research_center)
   mysql -h localhost -u username -p health_research_center < test-data/populate_healthcare_test_data.sql
   ```

2. **Run Tests:**
   ```bash
   cd backend/back
   mvn test -Dtest=ScalabilityTestSuite
   ```

3. **View Results:**
   ```bash
   cat test-results/scalability-results.csv
   ```

### 5.2 Cleanup

```bash
# PostgreSQL
psql -h localhost -U username -d ecommerce_db -c "DELETE FROM order_history WHERE order_id >= 9900000; DELETE FROM user_profiles WHERE tax_id LIKE 'TEST%'; DELETE FROM products WHERE product_id >= 990001;"

# MySQL
mysql -h localhost -u username -p health_research_center -e "DELETE FROM medical_records WHERE record_id >= 9900000; DELETE FROM patients WHERE tax_identifier LIKE 'TEST%';"
```

---

## 6. Raw Data

### 6.1 CSV Results Location

```
test-results/scalability-results.csv
```

### 6.2 CSV Format

| Column | Description |
|--------|-------------|
| timestamp | Test execution time (ISO 8601) |
| test_type | Category of test |
| scale | Data volume scale (Small/Medium/Large/XLarge) |
| expected_records | Expected number of records |
| actual_records | Actually returned records |
| duration_ms | Total query duration |
| avg_duration_ms | Average across runs |
| min_duration_ms | Fastest run |
| max_duration_ms | Slowest run |
| overhead_percent | Federation overhead |
| notes | Additional context |

---

## 7. Appendix

### 7.1 Test Configuration

```java
// Warmup runs before measurement
WARMUP_RUNS = 2

// Measurement runs for averaging
MEASUREMENT_RUNS = 5

// Controller ID for test databases
CONTROLLER_ID = 8L
```

### 7.2 SPARQL Query Used

```sparql
PREFIX schema: <http://schema.org/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

SELECT ?entity ?parentEntity ?property ?value
WHERE {
    ?person a schema:Person ;
            schema:taxID ?taxIdParam .

    {
        # Direct person properties
        ?person ?property ?value .
        BIND(?person AS ?entity)
        BIND(?person AS ?parentEntity)
        FILTER(?property != rdf:type && !isURI(?value))
    }
    UNION
    {
        # First-level entities (Orders, MedicalEntity, etc.)
        ?entity ?relationToPerson ?person ;
               ?property ?value .
        BIND(?entity AS ?parentEntity)
        FILTER(!isURI(?value) && ?property != ?relationToPerson)
    }
    UNION
    {
        # Second-level entities (Products in Orders)
        ?firstLevel ?relationToPerson ?person .
        ?firstLevel ?relationToSecondLevel ?entity .
        ?entity ?property ?value .
        BIND(?firstLevel AS ?parentEntity)
        FILTER(!isURI(?value))
        FILTER(?entity != ?person)
    }
}
```

### 7.3 Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-XX-XX | Vijon Baraku | Initial report template |

---

**Report Generated:** [Timestamp]
**OntoSov Version:** 0.0.1-SNAPSHOT
