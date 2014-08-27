
JAVA_FILES=$(shell find src -name "*.java")


all: libs/galil-java.jar

test: libs/galil-java.jar t/Test1.class
	java -enableassertions -classpath "t:libs/galil-java.jar" Test1

libs/galil-java.jar: ${JAVA_FILES}
	ant dist

t/%.class: t/%.java libs/galil-java.jar
	javac -g -classpath "t:libs/galil-java.jar" $<

check:
	checkstyle -c conf/checkstyle.xml  -r src

clean:
	ant clean
	rm t/*.class
