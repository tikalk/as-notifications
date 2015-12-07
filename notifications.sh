#!/bin/bash

echo "Starting Notifications Service..."

DIRNAME=`dirname $0`
PROJ_HOME=`cd $DIRNAME/.;pwd;`
export PROJ_HOME;

java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4jLogDelegateFactory -Dlog4j.configuration=file:$PROJ_HOME/conf/log4j.properties -jar $PROJ_HOME/build/libs/as-notifications-3.1.0-fat.jar -conf $PROJ_HOME/conf/conf.json