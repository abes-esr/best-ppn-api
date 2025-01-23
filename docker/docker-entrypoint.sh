#!/bin/bash

echo "Démarrage de l'application Java..."
java -XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:ConcGCThreads=5 -XX:+ExitOnOutOfMemoryError -XX:MaxGCPauseMillis=100 -jar /app/best-ppn-api.jar