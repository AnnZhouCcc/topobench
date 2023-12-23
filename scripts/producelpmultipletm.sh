export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/home/annzhou/gurobi912/linux64/lib
MYJAVAPATH="../"

# Compile
cd $MYJAVAPATH
javac -nowarn lpmaker/graphs/Graph.java
javac -nowarn lpmaker/ProduceLPMultipleTM.java
cd -

# Run
cd $MYJAVAPATH
java lpmaker/ProduceLPMultipleTM
# mv my.*.lp topology/my.lp
cd -

# Solve
method=2
./RunLPMultipleTM.sh ../my.0.lp $method -1
./RunLPMultipleTM.sh ../my.1.lp $method -1
./RunLPMultipleTM.sh ../my.2.lp $method 2
# rm ../topology/my.lp