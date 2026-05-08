#!/bin/bash
REGION=us-east-1

for STAGE in sales line quality packer shipping; do
  for PRIORITY in rush high normal; do
    aws sqs create-queue --queue-name ${STAGE}-${PRIORITY}-queue.fifo \
      --attributes FifoQueue=true,ContentBasedDeduplication=true \
      --region $REGION
  done
done

echo "All 15 queues created."