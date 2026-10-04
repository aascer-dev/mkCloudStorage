-- Reconcile capacity counters introduced before upload accounting existed.
-- Quota is based on active logical file records, rather than deduplicated
-- physical objects, so each user's visible file consumes its displayed size.
UPDATE storage_buckets AS bucket
LEFT JOIN (
    SELECT bucket_id, SUM(size) AS logical_used_storage
    FROM files
    WHERE status = 1
      AND is_folder = 0
    GROUP BY bucket_id
) AS usage_by_bucket ON usage_by_bucket.bucket_id = bucket.id
SET bucket.used_storage = COALESCE(usage_by_bucket.logical_used_storage, 0);
