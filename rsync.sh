set -ex
rsync -r -t -a --info=progress2 src pom.xml run.sh charm@virtual:~/AutoRoute

mvn clean install
