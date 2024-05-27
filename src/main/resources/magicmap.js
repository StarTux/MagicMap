'use strict';
var mapName = 'map_name';
var worldBorder = world_border;
var scalingFactor = 2.0;

function fixWorldBorder() {
    worldBorder.minX = (worldBorder.minX >> 9) << 9;
    worldBorder.maxX = (worldBorder.maxX >> 9) << 9;
    worldBorder.minZ = (worldBorder.minZ >> 9) << 9;
    worldBorder.maxZ = (worldBorder.maxZ >> 9) << 9;
}

fixWorldBorder();

function regionName(rx, rz) {
    return 'region.' + rx + '.' + rz;
}

function makeMapRegions() {
    const mapFrame = document.getElementById('map-frame');
    while (mapFrame.firstChild) {
        mapFrame.removeChild(mapFrame.lastChild);
    }
    const minRegionX = worldBorder.minX >> 9;
    const maxRegionX = worldBorder.maxX >> 9;
    const minRegionZ = worldBorder.minZ >> 9;
    const maxRegionZ = worldBorder.maxZ >> 9;
    for (var rz = minRegionZ; rz <= maxRegionZ; rz += 1) {
        for (var rx = minRegionX; rx <= maxRegionX; rx += 1) {
            const left = (rx - minRegionX) << 9;
            const top = (rz - minRegionZ) << 9;
            const mapRegion = document.createElement('img');
            mapRegion.id = regionName(rx, rz);
            mapRegion.className = 'map-region';
            mapRegion.draggable = false;
            mapRegion.style.top = top + 'px';
            mapRegion.style.left = left + 'px';
            mapRegion.border = 0;
            mapRegion.width = 512;
            mapRegion.height = 512;
            mapRegion.setAttribute('data-region-x', '' + rx);
            mapRegion.setAttribute('data-region-z', '' + rz);
            mapRegion.loading = 'lazy';
            mapFrame.appendChild(mapRegion);
        }
    }
}

function calculateFrame() {
    const scrolling = document.scrollingElement;
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    const top = scrolling.scrollTop;
    const left = scrolling.scrollLeft;
    const rax = (worldBorder.minX + left / scalingFactor) >> 9;
    const rbx = (worldBorder.minX + (left + width) / scalingFactor) >> 9;
    const raz = (worldBorder.minZ + top / scalingFactor) >> 9;
    const rbz = (worldBorder.minZ + (top + height) / scalingFactor) >> 9;
    const mapRegionList = document.getElementsByClassName('map-region');
    for (var i = 0; i < mapRegionList.length; i += 1) {
        const mapRegion = mapRegionList[i];
        const rx = parseInt(mapRegion.getAttribute('data-region-x'));
        const rz = parseInt(mapRegion.getAttribute('data-region-z'));
        if (rx >= rax && rx <= rbx && rz >= raz && rz <= rbz) {
            if (mapRegion.getAttribute('src')) continue;
            const url = '/map/' + mapName + '/r.' + rx + '.' + rz + '.png';
            mapRegion.setAttribute('src', url);
            //mapRegion.style['background-image'] = 'url("' + url + '")';
        } else {
            mapRegion.removeAttribute('src');
            //mapRegion.style['background-image'] = null;
        }
    }
}

var mouseDown = false;
var dragX = 0;
var dragY = 0;
var mouseX = 0;
var mouseY = 0;

window.addEventListener('load', event => {
    makeMapRegions();
    const mapFrame = document.getElementById('map-frame');
    const scrolling = document.scrollingElement;
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    scrolling.scrollTo(scalingFactor * (worldBorder.centerX - worldBorder.minX) - (width / 2),
                       scalingFactor * (worldBorder.centerZ - worldBorder.minZ) - (height / 2));
    document.onscrollend = event => calculateFrame();
    const mouseSurface = document;
    mouseSurface.onmousedown = event => {
        mouseDown = true;
        dragX = event.clientX;
        dragY = event.clientY;
        event.preventDefault();
    };
    mouseSurface.onmousemove = event => {
        mouseX = event.clientX;
        mouseY = event.clientY;
        if (!mouseDown) return;
        var x = event.clientX - dragX;
        var y = event.clientY - dragY;
        scrolling.scrollTo(scrolling.scrollLeft - x,
                           scrolling.scrollTop - y);
        dragX = event.clientX;
        dragY = event.clientY;
    };
    mouseSurface.onmouseup = event => {
        if (!mouseDown) return;
        mouseDown = false;
        event.preventDefault();
    };
    mouseSurface.onmouseleave = event => {
        mouseDown = false;
    };
    document.onwheel = event => setScalingFactor(Math.max(0.5, scalingFactor - event.deltaY * 0.001));
    websocket.addEventListener('websocketMessage', event => {
        switch (event.packet.id) {
        case 'chat': {
            const chatBox = document.getElementById('chat-box');
            const p = document.createElement('p');
            p.className = 'minecraft-chat-line';
            p.innerHTML = event.packet.html;
            chatBox.appendChild(p);
            chatBox.scrollTo(0, chatBox.scrollHeight);
            break;
        }
        case 'magicmap:player_add': {
            const player = document.createElement('img');
            const uuid = event.packet.player.uuid;
            const name = event.packet.player.name;
            player.id = 'live-player-' + uuid;
            player.className = 'live-player';
            player.src = '/skin/face/' + uuid + '.png';
            player.setAttribute('data-uuid', uuid);
            player.setAttribute('data-name', name);
            player.title = name;
            player.onclick = event => onClickLivePlayer(player, event);
            const left = event.packet.x - worldBorder.minX;
            const top = event.packet.z - worldBorder.minZ;
            player.style.top = top + 'px';
            player.style.left = left + 'px';
            document.getElementById('map-frame').appendChild(player);
            break;
        }
        case 'magicmap:player_update': {
            const player = document.getElementById('live-player-' + event.packet.player);
            if (!player) return;
            const left = event.packet.x - worldBorder.minX;
            const top = event.packet.z - worldBorder.minZ;
            player.style.top = top + 'px';
            player.style.left = left + 'px';
            break;
        }
        case 'magicmap:player_list': {
            const playerList = document.getElementById('player-list');
            while (playerList.firstChild) {
                playerList.removeChild(playerList.lastChild);
            }
            for (var i = 0; i < event.packet.players.length; i += 1) {
                const player = event.packet.players[i];
                const playerDiv = document.createElement('div');
                playerDiv.className = 'player-list-box';
                const img = document.createElement('img');
                img.className = 'player-list-face';
                img.src = '/skin/face/' + player.uuid + '.png';
                img.setAttribute('data-uuid', '' + player.uuid);
                img.setAttribute('data-name', player.name);
                img.title = player.name;
                img.onclick = event => onClickPlayerList(img, event);
                playerDiv.appendChild(img);
                const nameDiv = document.createElement('div');
                nameDiv.appendChild(document.createTextNode(player.name));
                nameDiv.className = 'minecraft-chat player-list-name';
                playerDiv.appendChild(nameDiv);
                playerList.appendChild(playerDiv);
            }
            break;
        }
        case 'magicmap:claim_update': {
            const claimId = event.packet.claimId;
            var claimRect = document.getElementById('claim-' + claimId);
            if (!event.packet.area) {
                if (claimRect) {
                    claimRect.parentElement.removeChild(claimRect);
                }
            } else {
                const ax = event.packet.area.ax;
                const ay = event.packet.area.ay;
                const bx = event.packet.area.bx;
                const by = event.packet.area.by;
                const left = ax - worldBorder.minX;
                const top = ay - worldBorder.minZ;
                if (!claimRect) {
                    claimRect = document.createElement('div');
                    claimRect.id = 'claim-' + claimId;
                    claimRect.className = 'live-claim';
                    claimRect.setAttribute('data-claim-id', '' + claimId);
                    claimRect.onclick = event => onClickClaim(claimRect, event);
                }
                claimRect.style.left = left + 'px';
                claimRect.style.top = top + 'px';
                claimRect.style.width = (bx - ax + 1) + 'px';
                claimRect.style.height = (by - ay + 1) + 'px';
                claimRect.title = event.packet.name;
                document.getElementById('map-frame').appendChild(claimRect);
            }
            break;
        }
        case 'magicmap:scroll_map': {
            const x = event.packet.x;
            const z = event.packet.z;
            scrollTo(x, z, true);
            break;
        }
        case 'magicmap:change_map': {
            mapName = event.packet.mapName;
            worldBorder = event.packet.worldBorder;
            fixWorldBorder();
            makeMapRegions();
            const mapFrame = document.getElementById('map-frame');
            mapFrame.innerHTML += event.packet.innerHtml;
            document.title = event.packet.displayName;
            calculateFrame();
            mapFrame.classList.remove('map-nether');
            mapFrame.classList.remove('map-the-end');
            if (event.packet.environment === 'NETHER') {
                mapFrame.classList.add('map-nether');
            } else if (event.packet.environment === 'THE_END') {
                mapFrame.classList.add('map-the-end');
            }
            scrollTo(event.packet.x, event.packet.z);
            sendServerMessage('magicmap:did_change_map');
            break;
        }
        case 'magicmap:show_tooltip': {
            removeTooltip();
            const scrolling = document.scrollingElement;
            const tooltip = parseHtml(event.packet.html)[0];
            tooltip.style.left = (mouseX + scrolling.scrollLeft) + 'px';
            tooltip.style.top = (mouseY + scrolling.scrollTop) + 'px';
            document.body.appendChild(tooltip);
            break;
        }
        default: break;
        }
    });
    mapFrame.onclick = event => {
        removeClaimHighlight();
        removeTooltip();
    };
    const chatBox = document.getElementById('chat-box');
    chatBox.scrollTo(0, chatBox.scrollHeight);
    calculateFrame();
});

function scrollTo(x, z, smooth = false) {
    const scrolling = document.scrollingElement;
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    const left = scalingFactor * (x - worldBorder.minX) - (width / 2);
    const top = scalingFactor * (z - worldBorder.minZ) - (height / 2);
    if (smooth) {
        scrolling.scrollTo({
            'left': left,
            'top': top,
            'behavior': 'smooth'
        });
    } else {
        scrolling.scrollTo(left, top);
    }
}

function onClickPlayerList(element, event) {
    sendServerMessage('magicmap:click_player_list', element.getAttribute('data-uuid'));
}

function onClickClaim(element, event) {
    removeTooltip();
    removeClaimHighlight();
    element.style['border-color'] = 'white';
    sendServerMessage('magicmap:click_claim', element.getAttribute('data-claim-id'));
    event.stopPropagation();
}

function onClickLivePlayer(element, event) {
    removeTooltip();
    removeClaimHighlight();
    sendServerMessage('magicmap:click_live_player', element.getAttribute('data-uuid'));
    event.stopPropagation();
}

function removeClaimHighlight() {
    const claims = document.getElementsByClassName('live-claim');
    for (var i = 0; i < claims.length; i += 1) {
        const claim = claims[i];
        claim.style['border-color'] = null;
    }
}

function removeTooltip() {
    const tooltip = document.getElementById('magicmap-tooltip');
    if (!tooltip) return;
    tooltip.parentElement.removeChild(tooltip);
}

function setScalingFactor(newScalingFactor) {
    const scrolling = document.scrollingElement;
    const w = scrolling.clientWidth;
    const h = scrolling.clientHeight;
    const x = (scrolling.scrollLeft + w / 2) / scrolling.scrollWidth;
    const y = (scrolling.scrollTop + h / 2) / scrolling.scrollHeight;
    scalingFactor = newScalingFactor;
    document.getElementById('map-frame').style.transform = 'scale(' + scalingFactor + ', ' + scalingFactor + ')';
    scrolling.scrollTo(x * scrolling.scrollWidth - (w / 2), y * scrolling.scrollHeight - (h / 2));
}
