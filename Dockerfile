###
# vert.x docker device-manager using a Java verticle packaged as a fatjar
# To build:
#  docker build -t fleettracker/as-notifications .
# To run:
#   docker run -t -i -p 8080:8080 fleettracker/as-notifications
###

FROM java:8



EXPOSE 5080

# Copy your fat jar to the container
ADD build/distributions/as-notifications-3.1.0.tar.gz /as-notifications

# Launch the verticle
WORKDIR /as-notifications

CMD ./notifications.sh
