#!/bin/bash

set -e
set -x

git log --oneline -n 5
sudo ./install-build-deps.sh
sudo start xvfb || true

for i in 3 4 5 6 7 8 9 10 11 12 13 14 15; do

rm *.zip || true
wget https://chromedriver.storage.googleapis.com/2.$i/chromedriver_linux64.zip
chmod +r *.zip
unzip -o -d buildtools *.zip
chmod +x buildtools/chromedriver

sudo ant runtest -Dtest.shuffle=1 -Dtest.repeat=1 || true

done
