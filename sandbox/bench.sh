#!/bin/bash

if [ -z "$1" ]; then
   echo "Missing submission file name parameter"
   exit 1
fi

if [ ! -r "${1}.tar.gz" ]; then
   echo "Can't read submission file ${1}.tar.gz"
   exit 1
fi

# Remove old stopped instances
OLD_CONTAINERS=$(echo $(docker ps -a -q --filter 'status=exited'))
#echo "Old containers: '${OLD_CONTAINERS}'"
if [ ! -z "${OLD_CONTAINERS}" ]; then
 #echo "docker rm ${OLD_CONTAINERS}"
 docker rm ${OLD_CONTAINERS}
fi

# Requires at least docker 1.3 for docker execz
SUBMISSION=$1
CURRENT=$(pwd)
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
TEST_DRIVER=${DIR}/testdriver
BASIC_TEST=${DIR}/basic.test
TEST_DIRECTORY=${DIR}/tests
STDOUT_LOG=${CURRENT}/${SUBMISSION}_stdout.tmp
STDERR_LOG=${CURRENT}/${SUBMISSION}_stderr.tmp
RESULT_FILE=${CURRENT}/${SUBMISSION}_result.tmp
STDOUT_LOG_FINAL=${CURRENT}/${SUBMISSION}_stdout.log
STDERR_LOG_FINAL=${CURRENT}/${SUBMISSION}_stderr.log
RESULT_FILE_FINAL=${CURRENT}/${SUBMISSION}_result.properties
APP_LOG=${DIR}/bencher.log
SAFETY_CMDS="ulimit -m 20971520; ulimit -u 4096; ulimit -n 4096; ulimit -f 307200; export PATH=$PATH:/usr/local/go/bin;"

MAX_MEMORY=20480
MAX_MEMORY_KB=20971520
LOG_CUTOFF=1024
UNPACK_TIMEOUT=10 # Not longer to control for disk space
COMPILE_TIMEOUT=60

DOCKER_RUN_OPTS="-m ${MAX_MEMORY}m --name=\"contest\" --net=\"none\" --cap-drop SETUID --cap-drop SETGID -v ${CURRENT}/${SUBMISSION}.tar.gz:/submission.tar.gz -v ${TEST_DIRECTORY}:/tests -v ${TEST_DRIVER}:/testdriver"
DOCKER_OPTS="--ip-forward=false --icc=false"

function shutdown {
   echo "[$(date)] Stopping $1" >> $APP_LOG
   docker stop -t 0 $1 >> $APP_LOG

   echo "[$(date)] Deleting $1" >> $APP_LOG
   docker rm $1 >> $APP_LOG

   #Truncate log files
   STDOUT_TRUNC=$(tail -c $LOG_CUTOFF $STDOUT_LOG)
   echo "${STDOUT_TRUNC}" > $STDOUT_LOG
   STDERR_TRUNC=$(tail -c $LOG_CUTOFF $STDERR_LOG)
   echo "${STDERR_TRUNC}" > $STDERR_LOG

   mv $STDOUT_LOG $STDOUT_LOG_FINAL
   mv $STDERR_LOG $STDERR_LOG_FINAL
   mv $RESULT_FILE $RESULT_FILE_FINAL

   echo "[$(date)] Finished submission ${SUBMISSION}" >> $APP_LOG
   echo "" >> $APP_LOG
}

function verify_return {
   if [ "$1" -ne "0" ]; then
      if [ "$1" -eq "124" ]; then
         echo "Timeout ... canceling run" >> ${STDERR_LOG}
         shutdown $2
      else
         echo "Program exited with error: $1 ... canceling run" >> ${STDERR_LOG}
         shutdown $2
      fi
      exit
   fi
}

function verify_run {
   if [ "$1" -ne "0" ]; then
      if [ "$1" -eq "124" ]; then
         echo "Timeout ... canceling run" >> ${STDERR_LOG}
         shutdown $2
      else
         echo "Wrong result ... canceling run " >> ${STDERR_LOG}
         echo "$3" >> ${STDERR_LOG}
         shutdown $2
      fi

      exit
   else
      echo "Passed test: $4" >> ${STDERR_LOG}
      echo "$4 = $3" >> ${RESULT_FILE}
   fi
}

function do_run {
   TEST="$1"
   TIMEOUT="$2"
   TEST_FILE="${TEST}.test"
   TEST_FILE_PATH="${DIR}/${TEST_FILE}"
   # Run small test
   ln -P ${TEST_FILE_PATH} ${TEST_DIRECTORY}/${TEST_FILE}
   echo "Running ${TEST} ..." >> ${STDOUT_LOG}
   WARMUP=$(timeout ${TIMEOUT} docker exec ${CONTAINER_ID} /bin/bash -c "${SAFETY_CMDS} cd ~/submission; cat /tests/${TEST_FILE} > /dev/null" 2>> ${STDERR_LOG})
   OUT=$(timeout ${TIMEOUT} docker exec ${CONTAINER_ID} /bin/bash -c "${SAFETY_CMDS} cd ~/submission; /testdriver ./run.sh /tests/${TEST_FILE}" 2>> ${STDERR_LOG})
   RET=$?
   rm ${TEST_DIRECTORY}/${TEST_FILE}
   verify_run $RET ${CONTAINER_ID} "$OUT" "${TEST}"
}

touch $RESULT_FILE

echo "[$(date)] Starting ${SUBMISSION} ... " >> $APP_LOG
CONTAINER_ID=$(docker ${DOCKER_OPTS} run ${DOCKER_RUN_OPTS}  -d -i -P sigmod15contest /bin/bash 2>> $APP_LOG)
echo "[$(date)] Started ${CONTAINER_ID}" >> $APP_LOG

docker exec ${CONTAINER_ID} /bin/bash -c 'mkdir ~/submission' > ${STDOUT_LOG} 2> ${STDERR_LOG}
verify_return $? ${CONTAINER_ID}

echo "Extracting ..." > ${STDOUT_LOG}
timeout $UNPACK_TIMEOUT docker exec ${CONTAINER_ID} /bin/bash -c "${SAFETY_CMDS} cd ~/submission; tar -xvf /submission.tar.gz" >> ${STDOUT_LOG} 2>> ${STDERR_LOG}
verify_return $? ${CONTAINER_ID}

echo "Compiling ..." >> ${STDOUT_LOG}
timeout $COMPILE_TIMEOUT docker exec ${CONTAINER_ID} /bin/bash -c "${SAFETY_CMDS} cd ~/submission; ./compile.sh" >> ${STDOUT_LOG} 2>> ${STDERR_LOG}
verify_return $? ${CONTAINER_ID}

do_run "small" 30

if [ -r "${DIR}/medium.test" ]; then
   do_run "medium" 60
fi

if [ -r "${DIR}/large.test" ]; then
   do_run "large" 180
fi


# TODO Truncate files
shutdown ${CONTAINER_ID}
