// index.js

// request message on server
//Calls KafkaServlet 
xhrGet("KafkaServlet", function(responseText){
	// add to document
	var mytitle = document.getElementById('message');
	mytitle.innerHTML = responseText;

}, function(err){
	console.log(err);
});

function postMessage(){
	var responseDiv = document.getElementById('postResponse');
	responseDiv.innerHTML = "<p>waiting for consumer...</p>";
	var postMessageBtn = document.getElementById('btnPost');
	postMessageBtn.value='request sent ...';
	xhrPost("KafkaServlet", function(responseText){
		var responseDiv = document.getElementById('postResponse');
		responseDiv.innerHTML = responseText;
		xhrGet("KafkaServlet", function(responseText){
			// add to document
			var mytitle = document.getElementById('message');
			mytitle.innerHTML = responseText;
			postMessageBtn.value='post message';
		
		}, function(err){
			console.log(err);
		});
	}, function(err){
		console.log(err);
	});
}

//utilities
function createXHR(){
	if(typeof XMLHttpRequest != 'undefined'){
		return new XMLHttpRequest();
	}else{
		try{
			return new ActiveXObject('Msxml2.XMLHTTP');
		}catch(e){
			try{
				return new ActiveXObject('Microsoft.XMLHTTP');
			}catch(e){}
		}
	}
	return null;
}
function xhrGet(url, callback, errback){
	var xhr = new createXHR();
	xhr.open("GET", url, true);
	xhr.onreadystatechange = function(){
		if(xhr.readyState == 4){
			if(xhr.status == 200){
				callback(xhr.responseText);
			}else{
				errback('service not available');
			}
		}
	};
	xhr.timeout = 3000;
	xhr.ontimeout = errback;
	xhr.send();
}

function xhrPost(url, callback, errback){
	var xhr = new createXHR();
	xhr.open("POST", url, true);
	xhr.onreadystatechange = function(){
		if(xhr.readyState == 4){
			if(xhr.status == 200){
				callback(xhr.responseText);
			}else{
				errback('XMLHttpRequest ready state: ' + xhr.readyState + '. Service not available');
			}
		}
	};
	xhr.timeout = 10000;
	xhr.ontimeout = errback;
	xhr.send();
}

function parseJson(str){
	return window.JSON ? JSON.parse(str) : eval('(' + str + ')');
}
function prettyJson(str){
	// If browser does not have JSON utilities, just print the raw string value.
	return window.JSON ? JSON.stringify(JSON.parse(str), null, '  ') : str;
}

