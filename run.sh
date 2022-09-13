set -ex

mvn compile assembly:single
java \
-ea -Xmx30m -XX:+UseParallelGC -Xlog:gc*:file=logs/gc.log \
-XX:+HeapDumpOnOutOfMemoryError -XX:NativeMemoryTracking=summary -XX:GCTimeLimit=15 \
-jar target/AutoRoute.jar