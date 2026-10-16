-- K-092: jeda minimum antar scan (menit). NULL = tanpa penggabungan scan berdekatan (K-047).
ALTER TABLE work_rules ADD COLUMN min_scan_gap_minutes INTEGER;
ALTER TABLE work_rules ADD CONSTRAINT work_rules_min_scan_gap_positive
    CHECK (min_scan_gap_minutes IS NULL OR min_scan_gap_minutes > 0);
