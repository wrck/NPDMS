SELECT b.*,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END AS event_type,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END AS rma_marked
FROM fb_shipment_barcode b
