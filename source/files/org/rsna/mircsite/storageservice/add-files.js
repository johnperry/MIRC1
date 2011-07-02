var fileCount = 0;

function captureFile() {
	var selectedfileElement = document.getElementById("selectedfile" + fileCount);
	fileCount++;

	var newInput = selectedfileElement.cloneNode(true);
	newInput.setAttribute("name","selectedfile" + fileCount);
	newInput.setAttribute("id","selectedfile" + fileCount);

	var br = document.createElement("BR");

	selectedfileElement.disabled = true;
	var parent = selectedfileElement.parentNode;
	parent.appendChild(br);
	parent.appendChild(newInput);
}

//Process all the input and submit the document
function submitForm() {
	//Enable all the non-blank selectedfile elements
	for (var i=0; i<fileCount; i++) {
		var sf = document.getElementById("selectedfile"+i);
		if (sf != null) {
			var t = sf.value;
			t = t.replace(/^\s+/g,"")	//trim the text
			t = t.replace(/\s+$/g,"");
			if (t != "") sf.disabled = false;
		}
	}
	document.getElementById('fileform').submit();
}
