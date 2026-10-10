# PuzzleIt backend: a PocketBase (https://pocketbase.io) with the collections of pb_migrations. Builds for amd64 and arm64 (Raspberry Pi 4/5, 64-bit OS).
FROM alpine:3.20
ARG PB_VERSION=0.40.5
ARG TARGETARCH
RUN apk add --no-cache ca-certificates unzip \
 && wget -q "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_${TARGETARCH}.zip" -O /tmp/pb.zip \
 && unzip -q /tmp/pb.zip -d /pb && rm /tmp/pb.zip
COPY pb_migrations /pb/pb_migrations
EXPOSE 8090
VOLUME /pb/pb_data
CMD ["/pb/pocketbase", "serve", "--http=0.0.0.0:8090", "--dir=/pb/pb_data", "--migrationsDir=/pb/pb_migrations"]
