#!/bin/bash

set -e
set -x

sudo apt-get update
sudo apt-get install vagrant
vagrant up
vagrant ssh -c 'cd /vagrant && yes | ./vagrant.sh'
