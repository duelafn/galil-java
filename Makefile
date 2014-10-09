
JAVA_FILES=$(shell find src -name "*.java")


all: libs/galil-java.jar

test: libs/galil-java.jar t/Test1.class
	java -enableassertions -classpath "t:libs/galil-java.jar" Test1

libs/galil-java.jar: ${JAVA_FILES} build.xml
	ant dist

t/%.class: t/%.java libs/galil-java.jar
	javac -g -classpath "t:libs/galil-java.jar" $<

check:
	JAVA_CMD=$(shell which java) checkstyle -c conf/checkstyle.xml  -r src 2>&1 | grep -v '^log4j:WARN'

clean:
	ant clean
	rm -f t/*.class
