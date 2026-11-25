/* Gráficos de /estadisticas (Chart.js 4.4.1). Colores desde los tokens de style.css. */
(function () {
  if (typeof window.Chart === 'undefined') return;
  var datos = window.SGI_ESTADISTICAS || {};
  var css = getComputedStyle(document.documentElement);
  function token(nombre) { return css.getPropertyValue(nombre).trim(); }

  var fg = token('--fg');
  var muted = token('--muted');
  var border = token('--border-strong') || token('--border');
  var colorEstado = {
    PLANIFICACION: token('--blue'),
    EN_CONSTRUCCION: token('--amber'),
    COMPLETADO: token('--green'),
    CANCELADO: token('--red')
  };

  var sinMovimiento = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  Chart.defaults.color = muted;
  Chart.defaults.borderColor = border;
  Chart.defaults.font.family = token('--font-body') || 'Karla, sans-serif';
  if (sinMovimiento) Chart.defaults.animation = false;

  function lienzo(id) { return document.getElementById(id); }

  var estados = datos.porEstado;
  if (estados && lienzo('grafico-estados')) {
    new Chart(lienzo('grafico-estados'), {
      type: 'doughnut',
      data: {
        labels: estados.labels,
        datasets: [{
          data: estados.valores,
          backgroundColor: estados.claves.map(function (k) { return colorEstado[k] || muted; }),
          borderColor: token('--surface'),
          borderWidth: 2
        }]
      },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { color: fg } } }
      }
    });
  }

  function barras(id, serie, color, etiqueta) {
    if (!serie || !lienzo(id)) return;
    new Chart(lienzo(id), {
      type: 'bar',
      data: {
        labels: serie.labels,
        datasets: [{ label: etiqueta, data: serie.valores, backgroundColor: color, borderRadius: 4 }]
      },
      options: {
        indexAxis: 'y',
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { beginAtZero: true, ticks: { precision: 0 } },
          y: { ticks: { color: fg }, grid: { display: false } }
        }
      }
    });
  }

  barras('grafico-usados', datos.masUsados, token('--amber'), 'Recetas que lo usan');
  barras('grafico-escasos', datos.escasos, token('--red'), 'Faltante');
})();
