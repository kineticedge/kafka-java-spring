#!/bin/sh

cd "$(dirname "$0")"

../gradlew assemble

. ./.classpath.sh

java -cp ${CP} io.kineticedge.a.Main "$@"
