###
# vert.x docker device-manager using a Java verticle packaged as a fatjar
# To build:
#  docker build -t fleettracker/ft-notifications .
# To run:
#   docker run -t -i -p 8080:8080 fleettracker/ft-notifications
###

FROM java:8



EXPOSE 5080

# Copy your fat jar to the container
ADD build/distributions/ft-notifications-3.1.0.tar.gz /ft-notifications

# Launch the verticle
WORKDIR /ft-notifications

CMD ./notifications.sh
