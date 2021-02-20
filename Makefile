all :
	@echo "Do one of the following:"
	@echo "make build"
	@echo "make publish"
	@echo "make list"

.PHONY : build
build :
	mvn clean package

.PHONY : publish
publish :
	aws lambda update-function-code --function-name test-check-publisher \
		--zip-file fileb://target/test-check-publisher-lambda.jar --publish

.PHONY : list
list :
	aws lambda list-versions-by-function --function-name test-check-publisher | grep Version

.PHONY : versions
versions :
	aws lambda list-versions-by-function --function-name test-check-publisher | \
		sed -e 's/.*Version[^0-9]*\([0-9][0-9]*\).*/\1/p' -e d
