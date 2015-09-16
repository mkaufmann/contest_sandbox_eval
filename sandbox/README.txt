./create.sh
	Creates the initial docker image from the Dockerfile

./bench.sh <submission_name>
	Compiles and runs the user program in <submission_name>.tar.gz using the testdriver
	(which has to be supplied) in a Docker container. Per run it will create three files.
	One each for stdin and stdout and one result file containing the runtime of the 
	user program.



Old?:

Adapt /ect/init.d/docker

In start change ulimit to

ulimit -n 4096

ulimit -u 1024
ulimit -p 1024

                ulimit -n 4096   
                if [ "$BASH" ]; then
                        ulimit -u 1024   
                else
                        ulimit -p 1024   
                fi
