#!/bin/sh

docker run -p3102:3102 -it --rm -v "$PWD":/usr/src/app -w /usr/src/app mado /bin/bash
#docker run --network="host" -h 127.0.0.1 -it --rm -v "$PWD":/usr/src/app -w /usr/src/app --name mado-run mado /bin/bash
#docker run -p3102:3102 -it --rm -v "$PWD":/usr/src/app -w /usr/src/app mado ./bin/clj-run