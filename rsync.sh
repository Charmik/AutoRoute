set -ex
rsync -r -t -a --info=progress2 src pom.xml config run.sh -e "ssh -i ~/.ssh/id_rsa_mac" --exclude 'config/telegramKey.txt' charm@virtual:~/AutoRoute

mvn clean install
