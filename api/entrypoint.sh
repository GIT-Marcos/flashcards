#!/bin/sh
set -e

echo "=========================================="
echo "  Flashcards API -- Container Starting"
echo "  Timestamp : $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "=========================================="

echo "  APP_PROFILE : ${APP_PROFILE:-dev}"

if [ -n "${DB_URL}" ]; then
    MASKED_DB_URL=$(echo "${DB_URL}" | sed 's|://[^:]*:[^@]*@|://****:****@|')
    echo "  DB_URL      : ${MASKED_DB_URL}"
else
    echo "  [ERROR] DB_URL is not set!"
    exit 1
fi

for var in DB_USER DB_PASSWORD JWT_SECRET_KEY; do
    if [ -z "$(eval echo \"\$${var}\")" ]; then
        echo "  [ERROR] ${var} is not set!"
        exit 1
    fi
done

echo "  Java        : $(java -version 2>&1 | head -1)"
echo "=========================================="

exec java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar
