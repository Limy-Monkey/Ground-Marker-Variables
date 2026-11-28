# Convert old markers

Ground Marker Variables versions before 2.0 copied labels compressed, as `"gmv": "1~…"` followed by unusual letters
and symbols. The plugin no longer reads those, so paste the copied text here to turn its labels back into plain text,
then copy the result and import it in the plugin as usual.

Everything happens in your browser: nothing you paste is sent anywhere.

<textarea id="gmv-old" rows="8" style="width: 100%; font-family: monospace;" placeholder='[{"regionId":12850,"regionX":10,"regionY":20,"z":0,"gmv":"1~…"}]'></textarea>

<button id="gmv-convert" class="md-button md-button--primary">Convert</button>
<button id="gmv-copy" class="md-button">Copy result</button>

<p id="gmv-status"></p>

<textarea id="gmv-new" rows="8" style="width: 100%; font-family: monospace;" readonly></textarea>

<script src="https://cdnjs.cloudflare.com/ajax/libs/pako/2.1.0/pako.min.js"></script>
