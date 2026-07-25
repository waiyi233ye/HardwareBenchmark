// === HardwareBenchmark 网站渲染逻辑 ===

document.addEventListener('DOMContentLoaded', function() {

  // 1. 模组描述
  document.getElementById('mod-desc').textContent = MOD_INFO.description;

  // 2. 命令表格
  const cmdTbody = document.querySelector('#commands-table tbody');
  COMMANDS.forEach(cmd => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><code>/hwbench ${cmd.name === 'hwbench' ? '' : cmd.name}</code></td>
      <td>${cmd.usage}</td>
      <td>${cmd.desc}</td>
      <td><code>${cmd.perm}</code></td>
      <td>${cmd.alias === '-' ? '-' : '<code>' + cmd.alias + '</code>'}</td>
    `;
    cmdTbody.appendChild(tr);
  });

  // 3. 核心模块
  const moduleGrid = document.getElementById('module-grid');
  CORE_MODULES.forEach(mod => {
    const div = document.createElement('div');
    div.className = 'module-card';
    div.innerHTML = `
      <div class="module-class">${mod.class}.java</div>
      <div class="module-desc">${mod.desc}</div>
    `;
    moduleGrid.appendChild(div);
  });

  // 4. 依赖库表格
  const depsTbody = document.querySelector('#deps-table tbody');
  DEPENDENCIES.forEach(dep => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>${dep.name}</strong></td>
      <td><span class="badge badge-version">${dep.version}</span></td>
      <td>${dep.purpose}</td>
    `;
    depsTbody.appendChild(tr);
  });

  // 5. JAR文件表格（Universal JAR）
  const jarsTbody = document.querySelector('#jars-table tbody');
  JAR_FILES.forEach(jar => {
    const tr = document.createElement('tr');
    const platformClass = jar.platform.toLowerCase();
    tr.innerHTML = `
      <td><code>${jar.filename}</code>${jar.merged ? ' <span class="badge badge-version">合并</span>' : ''}</td>
      <td><span class="tag tag-${platformClass}">${jar.platform}</span></td>
      <td>${jar.mc}</td>
      <td>Java ${jar.java}</td>
      <td>${jar.size}</td>
      <td>${jar.note || '-'}</td>
    `;
    jarsTbody.appendChild(tr);
  });

  // 5b. Legacy JAR 表格
  const legacyTbody = document.querySelector('#legacy-jars-table tbody');
  if (typeof LEGACY_JAR_FILES !== 'undefined') {
    LEGACY_JAR_FILES.forEach(jar => {
      const tr = document.createElement('tr');
      const platformClass = jar.platform.toLowerCase();
      tr.innerHTML = `
        <td><code>${jar.filename}</code></td>
        <td><span class="tag tag-${platformClass}">${jar.platform}</span></td>
        <td>${jar.mc}</td>
        <td>Java ${jar.java}</td>
        <td>${jar.size}</td>
        <td>${jar.note || '-'}</td>
      `;
      legacyTbody.appendChild(tr);
    });
  }

  // 5c. 合并策略说明
  renderMergeStrategy();

  // 6. 平台测试结果
  renderPlatformResults('bukkit', 'Bukkit');
  renderPlatformResults('fabric', 'Fabric');
  renderPlatformResults('forge', 'Forge');

  // 7. 跑分对比图表
  renderBenchmarkCharts();

  // 8. 硬件检测报告
  renderDetectReport();

  // 9. 库检查报告
  renderLibsReport();

  // 10. 问题修复列表
  renderIssues();

  // 11. 数据核实证据
  renderVerification();

  // 11. 导航高亮
  setupNavHighlight();
});

// === 渲染平台测试结果 ===
function renderPlatformResults(platformKey, platformName) {
  const tabsContainer = document.getElementById(platformKey + '-tabs');
  const contentContainer = document.getElementById(platformKey + '-content');
  const data = TEST_RESULTS[platformKey];
  const versions = Object.keys(data);

  // 创建标签
  versions.forEach((ver, idx) => {
    const btn = document.createElement('button');
    btn.className = 'version-tab' + (idx === 0 ? ' active' : '');
    btn.textContent = 'MC ' + ver;
    btn.onclick = function() {
      tabsContainer.querySelectorAll('.version-tab').forEach(t => t.classList.remove('active'));
      this.classList.add('active');
      renderVersionContent(platformKey, ver, contentContainer);
    };
    tabsContainer.appendChild(btn);
  });

  // 默认渲染第一个版本
  renderVersionContent(platformKey, versions[0], contentContainer);
}

function renderVersionContent(platformKey, version, container) {
  const data = TEST_RESULTS[platformKey][version];
  let html = '<div class="version-content">';

  // 服务器信息栏
  html += '<div class="server-info-bar">';
  html += `<div class="server-info-item"><span class="server-info-label">MC版本:</span><span class="server-info-value">${version}</span></div>`;
  html += `<div class="server-info-item"><span class="server-info-label">Java:</span><span class="server-info-value">${data.java}</span></div>`;
  html += `<div class="server-info-item"><span class="server-info-label">端口:</span><span class="server-info-value">${data.port}</span></div>`;
  html += `<div class="server-info-item"><span class="server-info-label">启动:</span><span class="status-pass">${data.startup}</span></div>`;
  html += `<div class="server-info-item"><span class="server-info-label">服务端:</span><span class="server-info-value">${data.serverJar}</span></div>`;
  if (data.loaderVersion) {
    html += `<div class="server-info-item"><span class="server-info-label">Loader:</span><span class="server-info-value">${data.loaderVersion}</span></div>`;
    html += `<div class="server-info-item"><span class="server-info-label">API:</span><span class="server-info-value">${data.apiVersion}</span></div>`;
  }
  if (data.detectMethod) {
    html += `<div class="server-info-item"><span class="server-info-label">检测方式:</span><span class="server-info-value">${data.detectMethod}</span></div>`;
  }
  html += '</div>';

  // 命令执行状态
  html += '<div class="server-info-bar" style="background:#e8f5e9">';
  html += `<div class="server-info-item"><span class="server-info-label">命令执行:</span><span class="status-pass">${data.commands.passed}/${data.commands.all} 通过</span></div>`;
  if (data.detect) {
    const detectClass = data.detect.includes('成功') ? 'status-pass' : (data.detect.includes('失败') ? 'status-fail' : 'status-warn');
    html += `<div class="server-info-item"><span class="server-info-label">硬件检测:</span><span class="${detectClass}">${data.detect}</span></div>`;
  }
  if (data.libs) {
    const libsClass = data.libs === 'OK' ? 'status-pass' : 'status-fail';
    html += `<div class="server-info-item"><span class="server-info-label">库检查:</span><span class="${libsClass}">${data.libs}</span></div>`;
  }
  html += '</div>';

  // 跑分结果卡片
  html += '<div class="benchmark-grid">';

  // CPU
  html += '<div class="benchmark-card">';
  html += '<div class="benchmark-title">CPU 跑分';
  if (data.cpu.rating) html += ` <span class="rating-badge rating-${data.cpu.rating.charAt(0)}">${data.cpu.rating}</span>`;
  html += '</div>';
  if (data.cpu.score !== null && data.cpu.score !== undefined) {
    html += `<div class="benchmark-score">${data.cpu.score}</div>`;
    if (data.cpu.duration) html += `<div class="benchmark-detail">耗时: ${data.cpu.duration}ms</div>`;
    if (data.cpu.donut) html += `<div class="benchmark-detail-row">🍩 甜甜圈: ${data.cpu.donut}</div>`;
    if (data.cpu.matrix) html += `<div class="benchmark-detail-row">🧮 矩阵: ${data.cpu.matrix}</div>`;
    if (data.cpu.prime) html += `<div class="benchmark-detail-row">🔢 质数: ${data.cpu.prime}</div>`;
    if (data.cpu.float) html += `<div class="benchmark-detail-row">⚡ 浮点: ${data.cpu.float}</div>`;
  } else if (data.cpu.status) {
    html += `<div class="benchmark-score fail">N/A</div>`;
    html += `<div class="benchmark-detail status-fail">${data.cpu.status}</div>`;
  }
  html += '</div>';

  // 内存
  html += '<div class="benchmark-card">';
  html += '<div class="benchmark-title">内存 跑分';
  if (data.mem.rating) html += ` <span class="rating-badge rating-${data.mem.rating.charAt(0)}">${data.mem.rating}</span>`;
  html += '</div>';
  if (data.mem.score !== null && data.mem.score !== undefined) {
    html += `<div class="benchmark-score">${data.mem.score}</div>`;
    if (data.mem.duration) html += `<div class="benchmark-detail">耗时: ${data.mem.duration}ms</div>`;
    if (data.mem.throughput) html += `<div class="benchmark-detail-row">📊 吞吐: ${data.mem.throughput}</div>`;
    if (data.mem.seqWrite) html += `<div class="benchmark-detail-row">📝 顺序写: ${data.mem.seqWrite}</div>`;
    if (data.mem.seqRead) html += `<div class="benchmark-detail-row">📖 顺序读: ${data.mem.seqRead}</div>`;
    if (data.mem.copy) html += `<div class="benchmark-detail-row">📋 复制: ${data.mem.copy}</div>`;
  } else if (data.mem.status) {
    html += `<div class="benchmark-score fail">N/A</div>`;
    const isFixed = data.mem.status.includes('已修复');
    html += `<div class="benchmark-detail ${isFixed ? 'status-warn' : 'status-fail'}">${data.mem.status}</div>`;
  }
  html += '</div>';

  // 磁盘
  html += '<div class="benchmark-card">';
  html += '<div class="benchmark-title">磁盘 跑分';
  if (data.disk.rating) html += ` <span class="rating-badge rating-${data.disk.rating.charAt(0)}">${data.disk.rating}</span>`;
  html += '</div>';
  if (data.disk.score !== null && data.disk.score !== undefined) {
    html += `<div class="benchmark-score">${data.disk.score}</div>`;
    if (data.disk.duration) html += `<div class="benchmark-detail">耗时: ${data.disk.duration}ms</div>`;
    if (data.disk.seqWrite) html += `<div class="benchmark-detail-row">📝 顺序写: ${data.disk.seqWrite}</div>`;
    if (data.disk.seqRead) html += `<div class="benchmark-detail-row">📖 顺序读: ${data.disk.seqRead}</div>`;
    if (data.disk.randRead) html += `<div class="benchmark-detail-row">🎲 随机读: ${data.disk.randRead}</div>`;
  } else if (data.disk.status) {
    html += `<div class="benchmark-score fail">N/A</div>`;
    const isFixed = data.disk.status.includes('已修复');
    html += `<div class="benchmark-detail ${isFixed ? 'status-warn' : 'status-fail'}">${data.disk.status}</div>`;
  }
  html += '</div>';

  // all 综合跑分
  html += '<div class="benchmark-card full-width">';
  html += '<div class="benchmark-title">All 综合跑分';
  if (data.all.rating) html += ` <span class="rating-badge rating-${data.all.rating.charAt(0)}">${data.all.rating}</span>`;
  html += '</div>';
  if (data.all.score !== null && data.all.score !== undefined) {
    html += `<div class="benchmark-score">${data.all.score}</div>`;
    if (data.all.cpuScore) html += `<div class="benchmark-detail-row">CPU: ${data.all.cpuScore}</div>`;
    if (data.all.memScore) html += `<div class="benchmark-detail-row">内存: ${data.all.memScore}</div>`;
    if (data.all.diskScore) html += `<div class="benchmark-detail-row">磁盘: ${data.all.diskScore}</div>`;
  } else {
    if (data.all.cpuScore) {
      html += `<div class="benchmark-detail-row">CPU: ${data.all.cpuScore} | 内存: ${data.all.memScore} | 磁盘: ${data.all.diskScore}</div>`;
    }
    if (data.all.note) {
      const isFixed = data.all.note.includes('已修复');
      html += `<div class="benchmark-detail ${isFixed ? 'status-warn' : ''}">${data.all.note}</div>`;
    }
  }
  html += '</div>';

  html += '</div>'; // benchmark-grid
  html += '</div>'; // version-content

  container.innerHTML = html;
}

// === 渲染 JAR 合并策略 ===
function renderMergeStrategy() {
  const container = document.getElementById('merge-strategy-content');
  if (!container || typeof JAR_MERGE_STRATEGY === 'undefined') return;
  const m = JAR_MERGE_STRATEGY;
  let html = '';

  // 顶部统计卡片
  html += '<div class="merge-stats">';
  html += `<div class="merge-stat-card"><div class="merge-stat-num">${m.beforeCount}</div><div class="merge-stat-label">合并前 JAR 数</div></div>`;
  html += `<div class="merge-stat-arrow">→</div>`;
  html += `<div class="merge-stat-card highlight"><div class="merge-stat-num">${m.afterCount}</div><div class="merge-stat-label">合并后 JAR 数</div></div>`;
  html += `<div class="merge-stat-card"><div class="merge-stat-num">${m.universalCount}</div><div class="merge-stat-label">Universal JAR</div></div>`;
  html += `<div class="merge-stat-card"><div class="merge-stat-num">${m.legacyCount}</div><div class="merge-stat-label">Legacy JAR</div></div>`;
  html += `<div class="merge-stat-card highlight"><div class="merge-stat-num">${m.reductionPercent}</div><div class="merge-stat-label">文件数减少</div></div>`;
  html += '</div>';

  // 策略说明
  html += '<div class="info-card">';
  html += `<h3 class="card-title">${m.strategy}</h3>`;
  html += `<p>${m.rationale}</p>`;
  html += `<p style="font-size:12px;color:#999;">验证日期: ${m.verifyDate} | 状态: ${m.verifyStatus}</p>`;
  html += '</div>';

  // 合并规则
  html += '<h3 class="sub-title">合并规则</h3>';
  html += '<div class="table-wrap"><table class="mc-table"><thead><tr><th>规则</th><th>说明</th></tr></thead><tbody>';
  m.mergeRules.forEach(r => {
    html += `<tr><td><strong>${r.rule}</strong></td><td>${r.detail}</td></tr>`;
  });
  html += '</tbody></table></div>';

  // 元数据调整
  html += '<h3 class="sub-title">元数据调整</h3>';
  html += '<div class="table-wrap"><table class="mc-table"><thead><tr><th>文件</th><th>调整内容</th></tr></thead><tbody>';
  m.metadataAdjustments.forEach(a => {
    html += `<tr><td><code>${a.file}</code></td><td>${a.changes.map(c => `<div>• ${c}</div>`).join('')}</td></tr>`;
  });
  html += '</tbody></table></div>';

  // 兼容层
  html += '<h3 class="sub-title">跨版本兼容层</h3>';
  html += '<div class="module-grid">';
  m.compatibilityLayer.forEach(c => {
    html += `<div class="module-card"><div class="module-class">${c.file}</div><div class="module-desc">${c.approach}</div></div>`;
  });
  html += '</div>';

  container.innerHTML = html;
}

// === 渲染跑分对比图表 ===
function renderBenchmarkCharts() {
  renderChart('cpu-chart', 'cpu');
  renderChart('mem-chart', 'mem');
  renderChart('disk-chart', 'disk');
}

function renderChart(containerId, benchType) {
  const container = document.getElementById(containerId);
  const allData = [];

  // 收集所有平台的跑分数据
  ['bukkit', 'fabric', 'forge'].forEach(platform => {
    const versions = TEST_RESULTS[platform];
    Object.keys(versions).forEach(ver => {
      const v = versions[ver];
      const bench = v[benchType];
      const score = bench.score;
      allData.push({
        label: `${platform} ${ver}`,
        platform: platform,
        score: score,
        status: bench.status
      });
    });
  });

  // 找最大值用于归一化
  const maxScore = Math.max(...allData.map(d => d.score || 0), 1);

  let html = '<div class="bar-chart">';
  allData.forEach(d => {
    const hasScore = d.score !== null && d.score !== undefined;
    const width = hasScore ? (d.score / maxScore * 100) : 0;
    const barClass = hasScore ? d.platform : 'fail';
    const scoreText = hasScore ? d.score.toFixed(2) : 'N/A';

    html += '<div class="bar-row">';
    html += `<div class="bar-label">${d.label}</div>`;
    html += `<div class="bar-track">`;
    html += `<div class="bar-fill ${barClass}" style="width:${width}%">${scoreText}</div>`;
    html += `</div>`;
    html += `</div>`;
  });
  html += '</div>';

  container.innerHTML = html;
}

// === 渲染硬件检测报告 ===
function renderDetectReport() {
  const container = document.getElementById('detect-report');
  const h = HARDWARE_INFO;
  const html = `<span class="detect-header">═══════════════════════════════════════════════════</span>
<span class="detect-header">              硬件信息检测报告</span>
<span class="detect-header">═══════════════════════════════════════════════════</span>

<span class="detect-section">【操作系统】</span>
  <span class="detect-key">系统:</span> <span class="detect-value">${h.os}</span>
  <span class="detect-key">运行时间:</span> <span class="detect-value">8小时32分钟</span>

<span class="detect-section">【CPU 处理器】</span>
  <span class="detect-key">型号:</span> <span class="detect-value">${h.cpuModel}</span>
  <span class="detect-key">物理核心:</span> <span class="detect-value">${h.physicalCores}</span>
  <span class="detect-key">逻辑线程:</span> <span class="detect-value">${h.logicalCores}</span>
  <span class="detect-key">最大频率:</span> <span class="detect-value">${h.maxFreq}</span>

<span class="detect-section">【内存】</span>
  <span class="detect-key">总内存:</span> <span class="detect-value">${h.memoryTotal}</span>
  <span class="detect-key">可用内存:</span> <span class="detect-value">${h.memoryAvailable}</span>
  <span class="detect-key">使用率:</span> <span class="detect-value">${h.memoryUsage}</span>

<span class="detect-section">【磁盘存储】</span>
  <span class="detect-key">设备:</span> <span class="detect-value">${h.disk}</span>

<span class="detect-section">【网络】</span>
  <span class="detect-key">接口:</span> <span class="detect-value">${h.network}</span>

<span class="detect-section">【Java运行时】</span>
  <span class="detect-key">Java版本:</span> <span class="detect-value">${h.javaVersion}</span>
  <span class="detect-key">JVM内存:</span> <span class="detect-value">${h.jvmMemory}</span>`;
  container.innerHTML = html;
}

// === 渲染库检查报告 ===
function renderLibsReport() {
  const container = document.getElementById('libs-report');
  let html = '';

  html += '<div class="libs-section">';
  html += '<div class="libs-section-title">包管理器</div>';
  html += `<p>检测到: <code>${LIBS_REPORT.packageManager}</code></p>`;
  html += '</div>';

  html += '<div class="libs-section">';
  html += '<div class="libs-section-title">Java 本地库 (JNA)</div>';
  html += `<p>状态: <span class="tool-status tool-installed">${LIBS_REPORT.jna}</span></p>`;
  html += `<p style="font-size:12px;color:#999;">java.library.path: <code>${LIBS_REPORT.javaLibPath}</code></p>`;
  html += '</div>';

  html += '<div class="libs-section">';
  html += '<div class="libs-section-title">系统工具库</div>';
  html += '<div class="table-wrap"><table class="mc-table"><thead><tr><th>工具名</th><th>状态</th><th>自动安装结果</th></tr></thead><tbody>';
  LIBS_REPORT.systemTools.forEach(tool => {
    const statusClass = tool.status === '已安装' ? 'tool-installed' : 'tool-missing';
    html += `<tr><td><strong>${tool.name}</strong></td><td><span class="tool-status ${statusClass}">${tool.status}</span></td><td>${tool.installResult}</td></tr>`;
  });
  html += '</tbody></table></div>';
  html += '</div>';

  html += '<div class="libs-section">';
  html += '<div class="libs-section-title">手动安装命令</div>';
  LIBS_REPORT.manualCommands.forEach(cmd => {
    html += `<p><code>${cmd}</code></p>`;
  });
  html += '</div>';

  container.innerHTML = html;
}

// === 渲染问题修复列表 ===
function renderIssues() {
  const container = document.getElementById('issue-list');
  FIXED_ISSUES.forEach(issue => {
    const div = document.createElement('div');
    div.className = 'issue-card fixed';
    div.innerHTML = `
      <div class="issue-header">
        <span class="issue-platform">${issue.platform}</span>
        <span class="issue-title">${issue.issue}</span>
        <span class="issue-fixed-badge">已修复</span>
      </div>
      <div class="issue-body">
        <p><strong>原因:</strong> ${issue.cause}</p>
        <p><strong>修复:</strong> ${issue.fix}</p>
      </div>
    `;
    container.appendChild(div);
  });
}

// === 渲染数据核实证据 ===
function renderVerification() {
  const container = document.getElementById('verification-content');
  const v = VERIFICATION_EVIDENCE;
  let html = '';

  // 核实摘要
  html += '<div class="verify-summary">';
  html += '<div class="verify-summary-title">核实摘要</div>';
  html += `<p class="verify-summary-text">${v.summary}</p>`;
  html += `<p class="verify-summary-date">核实时间: ${v.verifyDate}</p>`;
  html += '</div>';

  // 数据来源
  html += '<h3 class="sub-title">数据来源</h3>';
  html += '<div class="table-wrap"><table class="mc-table"><thead><tr><th>类型</th><th>路径</th><th>说明</th></tr></thead><tbody>';
  v.dataSources.forEach(src => {
    html += `<tr><td><strong>${src.type}</strong></td><td><code>${src.path}</code></td><td>${src.desc}</td></tr>`;
  });
  html += '</tbody></table></div>';

  // 核实详情
  html += '<h3 class="sub-title">核实详情</h3>';
  html += '<div class="verify-list">';
  v.verifications.forEach(ver => {
    const statusClass = ver.status.includes('修复') ? 'verify-fixed' : 'verify-confirmed';
    const statusIcon = ver.status.includes('修复') ? '🔧' : '✓';
    html += '<div class="verify-card ' + statusClass + '">';
    html += '<div class="verify-card-header">';
    html += `<span class="verify-platform">${ver.platform}</span>`;
    html += `<span class="verify-status ${statusClass}">${statusIcon} ${ver.status}</span>`;
    html += '</div>';
    html += `<div class="verify-card-body">`;
    html += `<p><strong>问题:</strong> ${ver.issue}</p>`;
    html += `<p><strong>发现:</strong> ${ver.finding}</p>`;
    if (ver.action !== '无需操作') {
      html += `<p><strong>操作:</strong> ${ver.action}</p>`;
    }
    html += `<p><strong>结果:</strong> <span class="verify-result">${ver.result}</span></p>`;
    html += `<p><strong>证据:</strong> <code class="verify-evidence">${ver.evidence}</code></p>`;
    html += `</div>`;
    html += '</div>';
  });
  html += '</div>';

  container.innerHTML = html;
}

// === 导航高亮 ===
function setupNavHighlight() {
  const links = document.querySelectorAll('.nav-link');
  const sections = document.querySelectorAll('.content-section');

  window.addEventListener('scroll', function() {
    let current = '';
    sections.forEach(section => {
      const rect = section.getBoundingClientRect();
      if (rect.top <= 100 && rect.bottom >= 100) {
        current = section.id;
      }
    });
    links.forEach(link => {
      link.classList.remove('active');
      if (link.getAttribute('href') === '#' + current) {
        link.classList.add('active');
      }
    });
  });
}
