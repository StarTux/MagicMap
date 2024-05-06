const mapName = "map_name";
const worldBorder = world_border;

worldBorder.minX = (worldBorder.minX >> 9) << 9;
worldBorder.maxX = (worldBorder.maxX >> 9) << 9;
worldBorder.minZ = (worldBorder.minZ >> 9) << 9;
worldBorder.maxZ = (worldBorder.maxZ >> 9) << 9;

function calculateFrame() {
    const width = document.scrollingElement.clientWidth;
    const height = document.scrollingElement.clientHeight;
    const top = document.scrollingElement.scrollTop;
    const left = document.scrollingElement.scrollLeft;
    const rax = (worldBorder.minX + left) >> 9;
    const rbx = (worldBorder.minX + left + width) >> 9;
    const raz = (worldBorder.minZ + top) >> 9;
    const rbz = (worldBorder.minZ + top + height) >> 9;
    console.log({
	'top': top,
	'minZ': worldBorder.minZ,
	'sum': (top + worldBorder.minZ),
	'raz': raz
    });
    for (var rz = raz; rz <= rbz; rz += 1) {
	for (var rx = rax; rx <= rbx; rx += 1) {
	    var img = document.getElementById("r." + rx + "." + rz);
	    if (!img) continue;
	    const oldSrc = img.getAttribute("src");
	    if (oldSrc && oldSrc !== '') continue;
	    img.setAttribute("src", "/map/" + mapName + "/r." + rx + "." + rz + ".png");
	}
    }
}

function onScroll(event) {
    calculateFrame();
};

window.onload = function() {
    const width = document.scrollingElement.clientWidth;
    const height = document.scrollingElement.clientHeight;
    document.scrollingElement.scrollTo(worldBorder.centerX - worldBorder.minX - (width / 2),
				       worldBorder.centerZ - worldBorder.minZ - (height / 2));
    document.onscrollend = onScroll;
};
