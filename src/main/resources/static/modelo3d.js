(function () {
  var sceneEl = document.getElementById('m3d-scene');
  var selector = document.getElementById('m3d-selector');
  var infoNombre = document.getElementById('m3d-info-nombre');
  var infoTipo = document.getElementById('m3d-info-tipo');
  var infoCategoria = document.getElementById('m3d-info-categoria');
  var hintEl = document.getElementById('m3d-hint');
  if (!sceneEl) return;

  var W = sceneEl.clientWidth || 760;
  var H = sceneEl.clientHeight || 520;

  var renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(W, H);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.NoToneMapping;
  renderer.toneMappingExposure = 1.0;
  renderer.outputEncoding = THREE.sRGBEncoding;
  sceneEl.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  scene.background = new THREE.Color(0x10131a);
  scene.fog = new THREE.Fog(0x10131a, 8, 18);

  var camera = new THREE.PerspectiveCamera(45, W / H, 0.1, 100);
  camera.position.set(2.4, 1.9, 3.0);
  camera.lookAt(0, 0, 0);

  scene.add(new THREE.AmbientLight(0xffffff, 0.65));
  var sun = new THREE.DirectionalLight(0xffeedd, 1.1);
  sun.position.set(3, 6, 4);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  sun.shadow.camera.near = 0.5; sun.shadow.camera.far = 20;
  sun.shadow.camera.left = -3; sun.shadow.camera.right = 3;
  sun.shadow.camera.top = 3; sun.shadow.camera.bottom = -3;
  scene.add(sun);
  var rim = new THREE.DirectionalLight(0xffffff, 0.45);
  rim.position.set(-3, 2, -3);
  scene.add(rim);

  var base = new THREE.Mesh(
    new THREE.CircleGeometry(1.4, 48),
    new THREE.MeshStandardMaterial({ color: 0x1c2230, roughness: 0.9, metalness: 0 })
  );
  base.rotation.x = -Math.PI / 2;
  base.position.y = -1.02;
  base.receiveShadow = true;
  scene.add(base);
  var grilla = new THREE.GridHelper(5, 10, 0x3a4150, 0x2a3040);
  grilla.position.y = -1.0;
  scene.add(grilla);

  var grupo = new THREE.Group();
  scene.add(grupo);

  var controls = new THREE.OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.minDistance = 1.5;
  controls.maxDistance = 8;
  controls.maxPolarAngle = Math.PI * 0.85;

  var loader = new THREE.TextureLoader();
  loader.setCrossOrigin('anonymous');

  function loadTex(path) {
    var t = loader.load(path);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestMipmapNearestFilter;
    t.generateMipmaps = true;
    t.encoding = THREE.sRGBEncoding;
    return t;
  }

  var TEX = {
    'block/grass': {
      top: '/textures/block/grass_block_top.png',
      side: '/textures/block/grass_block_side.png',
      bottom: '/textures/block/dirt.png'
    },
    'block/dirt': { all: '/textures/block/dirt.png' },
    'block/stone': { all: '/textures/block/stone.png' },
    'block/sand': { all: '/textures/block/sand.png' },
    'block/gravel': { all: '/textures/block/gravel.png' },
    'block/wool': { all: '/textures/block/wool.png' },
    'block/cobblestone': { all: '/textures/block/cobblestone.png' },
    'block/oak_planks': { all: '/textures/block/oak_planks.png' },
    'block/obsidian': { all: '/textures/block/obsidian.png' },
    'block/bedrock': { all: '/textures/block/bedrock.png' },
    'block/crafting': {
      top: '/textures/block/crafting_table_top.png',
      side: '/textures/block/crafting_table_side.png',
      bottom: '/textures/block/crafting_table_side.png'
    },
    'block/log': {
      top: '/textures/block/log_top.png',
      side: '/textures/block/log_side.png',
      bottom: '/textures/block/log_top.png'
    },
    'item/diamond': '/textures/item/diamond.png',
    'item/emerald': '/textures/item/emerald.png',
    'item/iron_ingot': '/textures/item/iron_ingot.png',
    'item/apple': '/textures/item/apple.png',
    'item/stick': '/textures/item/stick.png',
    'item/book': '/textures/item/book.png',
    'item/diamond_pickaxe': '/textures/item/diamond_pickaxe.png',
    'item/stone_pickaxe': '/textures/item/stone_pickaxe.png',
    'item/stone': '/textures/item/stone.png',
    'item/crafting_table': '/textures/item/crafting_table.png',
    'item/vidrio': '/textures/item/vidrio.png'
  };

  var COLORES_ITEM = {
    'item/diamond': 0x4aedc9,
    'item/emerald': 0x3ec46d,
    'item/iron_ingot': 0xd8d8d8,
    'item/apple': 0xc1442d,
    'item/stick': 0x8a6a3a,
    'item/book': 0x7a3a8a,
    'item/diamond_pickaxe': 0x4aedc9,
    'item/stone_pickaxe': 0x9e9e9e,
    'item/stone': 0x9e9e9e,
    'item/crafting_table': 0xc9a27c,
    'item/vidrio': 0x9fd8e8
  };

  function colorPorCategoria(cat) {
    if (!cat) return 0x7f7f7f;
    if (cat.indexOf('Materias') === 0) return 0x4aedc9;
    if (cat.indexOf('Bloques') === 0) return 0xc9a27c;
    if (cat.indexOf('Utilidad') === 0) return 0xe01f36;
    if (cat.indexOf('Herramientas') === 0) return 0xd8d8d8;
    return 0x7f7f7f;
  }

  function texturaProcedural(color) {
    var c = document.createElement('canvas');
    c.width = c.height = 128;
    var ctx = c.getContext('2d');
    ctx.fillStyle = '#' + new THREE.Color(color).getHexString();
    ctx.fillRect(0, 0, 128, 128);
    for (var y = 0; y < 4; y++) {
      for (var x = 0; x < 4; x++) {
        if ((x + y) % 2 === 0) {
          ctx.fillStyle = 'rgba(255,255,255,0.08)';
          ctx.fillRect(x * 32, y * 32, 32, 32);
        }
      }
    }
    var tex = new THREE.CanvasTexture(c);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.encoding = THREE.sRGBEncoding;
    return tex;
  }

  function materialDesde(tex) {
    if (tex instanceof THREE.Texture) {
      return new THREE.MeshStandardMaterial({ map: tex, roughness: 0.55 });
    }
    return new THREE.MeshStandardMaterial({ color: tex, roughness: 0.55 });
  }

  function materialItem(tex, color, alpha, translucido) {
    var opts = {
      transparent: !!translucido,
      alphaTest: alpha || 0.5,
      depthWrite: !translucido,
      side: THREE.FrontSide,
      roughness: translucido ? 0.3 : 0.85
    };
    if (translucido) opts.opacity = 0.85;
    if (tex instanceof THREE.Texture) {
      opts.map = tex;
      opts.color = 0xffffff;
    } else {
      opts.color = color;
    }
    return new THREE.MeshStandardMaterial(opts);
  }

  function limpiar() {
    while (grupo.children.length) {
      var m = grupo.children[0];
      grupo.remove(m);
      if (m.geometry) m.geometry.dispose();
      if (Array.isArray(m.material)) m.material.forEach(function (mat) { if (mat.map) mat.map.dispose(); });
      else if (m.material && m.material.map) m.material.map.dispose();
    }
  }

  function resolverTex(token) {
    var def = TEX[token];
    if (!def) return null;
    if (typeof def === 'string') return loadTex(def);
    var res = {};
    if (def.all) {
      res.all = loadTex(def.all);
    } else {
      if (def.top) res.top = loadTex(def.top);
      if (def.side) res.side = loadTex(def.side);
      if (def.bottom) res.bottom = loadTex(def.bottom);
    }
    return res;
  }

  function cargarIcono(url, onTex) {
    var img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = function () {
      var c = document.createElement('canvas');
      c.width = img.naturalWidth || 128;
      c.height = img.naturalHeight || 128;
      var ctx = c.getContext('2d');
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(img, 0, 0);
      var tex = new THREE.CanvasTexture(c);
      tex.magFilter = THREE.NearestFilter;
      tex.minFilter = THREE.NearestFilter;
      tex.generateMipmaps = false;
      tex.encoding = THREE.sRGBEncoding;
      tex.needsUpdate = true;
      onTex(tex);
    };
    img.onerror = function () { onTex(null); };
    img.src = url;
  }

  function mostrar(item) {
    limpiar();
    infoNombre.textContent = item.nombre || 'Ítem sin nombre';
    infoTipo.textContent = item.tipoVisual || 'ITEM_PLANO';
    infoCategoria.textContent = item.categoria || '';
    var catColor = colorPorCategoria(item.categoria);
    var tex = resolverTex(item.textura);
    var tipo = item.tipoVisual;

    if (tipo === 'BLOQUE') {
      var geoBloque = new THREE.BoxGeometry(1, 1, 1);
      if (tex && tex.top && tex.side) {
        var mats = [
          materialDesde(tex.side), materialDesde(tex.side),
          materialDesde(tex.top),
          materialDesde(tex.bottom || tex.side),
          materialDesde(tex.side), materialDesde(tex.side)
        ];
      } else if (tex && tex.all) {
        var m = materialDesde(tex.all);
        mats = [m, m, m, m, m, m];
      } else {
        var pm = materialDesde(texturaProcedural(catColor));
        mats = [pm, pm, pm, pm, pm, pm];
      }
      var bloque = new THREE.Mesh(geoBloque, mats);
      bloque.castShadow = true;
      bloque.receiveShadow = true;
      grupo.add(bloque);
    } else {
      var esVidrio = item.textura === 'item/vidrio' || item.fullId === 'minecraft:glass';
      var geoItem = new THREE.PlaneGeometry(1.2, 1.2);
      var colorItem = COLORES_ITEM[item.textura] || catColor;
      var alpha = esVidrio ? 0.05 : 0.5;

      var caraFront = materialItem(tex, colorItem, alpha, esVidrio);

      var texBack = tex instanceof THREE.Texture ? tex.clone() : tex;
      if (texBack instanceof THREE.Texture) {
        texBack.wrapS = THREE.RepeatWrapping;
        texBack.repeat.x = -1;
        texBack.needsUpdate = true;
      }
      var caraBack = materialItem(texBack, colorItem, alpha, esVidrio);

      var CAPAS = esVidrio ? 2 : 10;
      var SEP = 0.012;
      for (var p = 0; p < CAPAS; p++) {
        var pagina = new THREE.Mesh(geoItem, p === 0 ? caraBack : caraFront);
        pagina.position.z = (p - (CAPAS - 1) / 2) * SEP;
        if (p === 0) pagina.rotation.y = Math.PI;
        grupo.add(pagina);
      }

      var imagenFinal = item.imagen;
      if (esVidrio) {
        imagenFinal = 'https://blocksitems.com/api/v1/items/minecraft:glass_pane/icon?size=128';
      }
      if (imagenFinal) {
        cargarIcono(imagenFinal, function (tex) {
          if (!tex) return; // se mantiene el fallback local/procedural
          caraFront.map = tex;
          caraFront.color.setHex(0xffffff);
          caraFront.needsUpdate = true;
          var back = tex.clone();
          back.wrapS = THREE.RepeatWrapping;
          back.repeat.x = -1;
          back.needsUpdate = true;
          caraBack.map = back;
          caraBack.color.setHex(0xffffff);
          caraBack.needsUpdate = true;
        });
      }
    }
  }

  function seleccionar(opt) {
    if (!opt || !opt.value) {
      infoNombre.textContent = 'Selecciona un ítem';
      infoTipo.textContent = '';
      infoCategoria.textContent = '';
      return;
    }
    mostrar({
      nombre: opt.dataset.nombre,
      categoria: opt.dataset.categoria,
      tipoVisual: opt.dataset.tipo,
      textura: opt.dataset.textura,
      fullId: opt.dataset.fullid,
      imagen: opt.dataset.imagen
    });
  }

  if (selector) {
    selector.addEventListener('change', function () {
      seleccionar(selector.options[selector.selectedIndex]);
    });
    if (selector.options.length > 1) seleccionar(selector.options[1]);
  } else if (hintEl) {
    hintEl.textContent = 'No hay ítems todavía. Crea algunos en /items.';
  }

  var clock = new THREE.Clock();
  function animate() {
    requestAnimationFrame(animate);
    var t = clock.getElapsedTime();
    grupo.position.y = Math.sin(t * 1.3) * 0.15;
    grupo.rotation.y += 0.008;
    controls.update();
    renderer.render(scene, camera);
  }
  animate();

  function onResize() {
    W = sceneEl.clientWidth || 760;
    H = sceneEl.clientHeight || 520;
    camera.aspect = W / H;
    camera.updateProjectionMatrix();
    renderer.setSize(W, H);
  }
  window.addEventListener('resize', onResize);
})();