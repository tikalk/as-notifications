###
# vert.x docker device-manager using a Java verticle packaged as a fatjar
# To build:
#  docker build -t fleettracker/as-notifications .
# To run:
#   docker run -t -i -p 8080:8080 fleettracker/as-notifications
###

FROM java:8

ENV VERTICLE_FILE as-notifications-3.1.0-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your fat jar to the container
COPY build/libs/$VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar $VERTICLE_FILE"]
