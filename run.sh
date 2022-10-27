set -ex

#mvn install

mvn compile assembly:single
java \
-ea -Xmx400m -XX:+UseSerialGC -Xlog:gc*:file=logs/gc.log \
-XX:+HeapDumpOnOutOfMemoryError -XX:NativeMemoryTracking=summary -XX:GCTimeLimit=50 \
-jar target/AutoRoute.jar