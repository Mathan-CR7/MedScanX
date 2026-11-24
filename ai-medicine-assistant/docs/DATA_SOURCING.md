# Data Sourcing, Ingestion Pipeline & Verification Documentation

This document explains the architecture of the real medicine data ingestion pipeline, data sources, database constraints, verification meaning, and legal/ToS considerations.

---

## 1. Data Sources & Ingestion Pipeline

### Catalog Ingestion (`MedicineCatalogIngestionService`)
- **Data Sources**: Retail pharmacy catalogs (e.g. PharmEasy, NetMeds, 1mg, Apollo) scraped using Playwright headless Chromium crawlers.
- **Ingested Data Fields**:
  - `brandName`: Marketed trade/brand name (e.g., "Dolo 650").
  - `genericName`: Active pharmaceutical ingredient (API) / composition (e.g., "Paracetamol").
  - `strength`: Unit dosage strength (e.g., "650 mg", "40 mg").
  - `dosageForm`: Form of medication (e.g., "Tablet", "Capsule", "Syrup").
  - `manufacturer`: Pharmaceutical company / marketer (e.g., "Micro Labs Limited").
  - `source`: Retail source identifier (e.g., "PharmEasy", "Netmeds").
  - `sourceReference`: Direct product URL or canonical product ID.
- **Cadence**: Scheduled daily at 02:00 AM via `CatalogIngestionScheduler`.
- **Database Uniqueness & Deduplication**:
  - Enforced by database unique constraint `uk_brand_strength_mfg` on `(normalized_brand_name, strength, normalized_manufacturer)`.
  - Re-ingesting an existing product updates the record (`updatedAt`, `sourceReference`) without creating duplicate database rows.

### Live Price Scraping (`MedicinePriceService` & `PlaywrightScraperService`)
- **Data Sources**: Real-time Playwright web scrapers querying 1mg, Apollo Pharmacy, NetMeds, and PharmEasy.
- **Cadence**: On-demand during image/brand search requests, and scheduled every 6 hours via `ScraperScheduler`.

---

## 2. Loud Failure Policy (Zero Silent Fallbacks)

The system adheres strictly to a **No Silent Fallback Policy**:
- **No Static Seed Data**: Static seed lists and dummy CDSCO records have been completely removed.
- **Unreachable Sources**: If a catalog source is unreachable or returns zero records during an ingestion run, the ingestion pipeline logs a high-priority `ERROR` and throws an exception (`IllegalStateException`). It **never** falls back to fabricated or stale un-marked data.
- **Missing Brand Matches**: If OCR candidate text cannot be matched against catalog records with sufficient confidence (threshold 0.50), the system explicitly returns `brandName: "UNVERIFIED"`.

---

## 3. Scope of "Verified" Status

> [!IMPORTANT]
> **What "Verified" Means**:
> The `verified` flag (`status: VERIFIED`) indicates that the extracted OCR label text (brand name, composition, manufacturer, strength) has been matched against **real ingested retail catalog records**.

> [!WARNING]
> **What "Verified" Does NOT Mean**:
> "Verified" status does **NOT** indicate physical anti-counterfeiting verification, batch serial number validation, or regulatory CDSCO lab testing. It certifies catalog metadata matching only.

---

## 4. Scalable Matching Architecture

- **Database Pre-filtering**: `MedicineMatchingService` uses an indexed SQL pre-filter query (`findByNormalizedBrandNameStartingWith` / `LIKE prefix%`) against the MySQL `idx_norm_brand` index.
- **Candidate Subset Scoring**: Jaro-Winkler fuzzy similarity scoring runs exclusively against the pre-filtered candidate subset (~20-50 rows), reducing execution time from $O(N)$ full table scan to $O(\log N)$ range lookup.

---

## 5. Terms of Service & Legal Considerations

- **Crawling Compliance**: Catalog and price scraping are conducted using rate-limited Playwright browser sessions with custom user-agents.
- **Fair Use & Data Usage**: Product names, compositions, and prices are collected solely for price comparison and identification purposes.
