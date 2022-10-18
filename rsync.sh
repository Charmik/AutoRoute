set -ex
rsync -r -t -a --info=progress2 --exclude 'debug' --exclude 'logs' --exclude 'target' --exclude 'config/cache' --exclude 'config/visit' --exclude 'config/telegramKey.txt' --exclude 'tmp' . charm@virtual:~/

mvn clean install
