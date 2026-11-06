'use strict';
// Separate candidate packaging; never writes a native MSI/freeze acceptance marker.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { createRequire } = require('node:module');
const { verifyFrontend } = require('./verify-frontend.cjs');
async function main() {
  const [output, frontend, bridge, builderDirectory] = process.argv.slice(2).map(x => path.resolve(x));
  if (!output || !frontend || !bridge || !builderDirectory || process.argv.length !== 6 || fs.existsSync(output))
    throw Error('Usage: package-windows-service-candidate.cjs NEW_OUTPUT VERIFIED_FRONTEND BRIDGE_OUTPUT BUILDER_DIRECTORY');
  const builderRequire = createRequire(path.join(builderDirectory, 'package.json'));
  if (builderRequire('electron-builder/package.json').version !== '26.0.12') throw Error('Pinned electron-builder 26.0.12 required');
  const frontendRecord = await verifyFrontend(frontend);
  const bridgeRecord = JSON.parse(fs.readFileSync(path.join(bridge, 'bridge-manifest.json'), 'utf8'));
  const helper = fs.readFileSync(path.join(bridge, 'NpdmsServiceBridge.exe'));
  if (bridgeRecord.format !== 1 || bridgeRecord.sha256 !== crypto.createHash('sha256').update(helper).digest('hex') ||
      bridgeRecord.bytes !== helper.length || bridgeRecord.releaseFrozen !== false ||
      bridgeRecord.ownershipBoundary !== 'scm-process-listener') throw Error('Candidate bridge manifest mismatch');
  fs.mkdirSync(output, { recursive: true });
  const project = path.join(output, 'app-source'), app = path.resolve(__dirname, '..');
  fs.mkdirSync(project);
  fs.cpSync(path.join(app, 'src'), path.join(project, 'src'), { recursive: true });
  fs.mkdirSync(path.join(project, 'resources'));
  for (const name of ['index.html', 'renderer.js', 'style.css']) fs.copyFileSync(path.join(app, 'resources', name), path.join(project, 'resources', name));
  fs.cpSync(frontend, path.join(project, 'resources/frontend'), { recursive: true });
  fs.mkdirSync(path.join(project, 'resources/windows-service'));
  for (const name of ['NpdmsServiceBridge.exe', 'bridge-manifest.json']) fs.copyFileSync(path.join(bridge, name), path.join(project, 'resources/windows-service', name));
  const sourcePackage = JSON.parse(fs.readFileSync(path.join(app, 'package.json')));
  fs.writeFileSync(path.join(project, 'package.json'), JSON.stringify({ name: sourcePackage.name, version: sourcePackage.version,
    private: true, main: sourcePackage.main, description: 'Windows installed-DAC service integration candidate' }, null, 2));
  const notice = 'Windows 安装与集成验证候选（未签名、未经真实 Windows 验收）\r\n\r\n' +
    '本包安装 Electron 桌面客户端和 Windows 服务适配器。须另有已安装的原生 Windows DAC（含 Java/JAR/WinSW）及 .NET Framework 4.6.1 或更高版本。\r\n' +
    '本包不安装、升级或替换原生 DAC 服务，不包含完整自给运行的 DAC。原生配置中的后端和网页来源均须与桌面输入的 HTTPS 地址一致。\r\n' +
    '点击启动/连接后，适配器才启动已有服务并校验身份。断开或退出桌面不会停止共享服务。服务密钥继续由原生 DPAPI 存储，桌面不读取密钥。\r\n' +
    '真实离线业务、Windows 合成离线试验未启用；常用命令预设目录仍缺失。正式冻结校验未完成，不能视为正式发行版本。\r\n' +
    '请仅在 Windows 测试机器验证安装、连接和卸载；保留现有原生 DAC 数据与配置。\r\n';
  fs.writeFileSync(path.join(project, 'CANDIDATE-NOTICE.txt'), '\ufeff' + notice);
  fs.writeFileSync(path.join(output, 'candidate-build.json'), JSON.stringify({ candidate: true, frontend: frontendRecord,
    bridge: bridgeRecord, nativeDacInstalledByThisPackage: false, windowsExecutionTested: false, signed: false }, null, 2));
  if (process.platform === 'linux') {
    const { UninstallerReader } = builderRequire('app-builder-lib/out/targets/nsis/nsisUtil');
    const wine = builderRequire('app-builder-lib/out/wine');
    wine.execWine = async (file, file64, args) => {
      if (path.dirname(file) !== output || file64 !== null || args.length !== 0) throw Error('Unexpected executable request');
      await UninstallerReader.exec(file, path.join(output, '__uninstaller-nsis-npdms-desktop.exe'));
    };
  }
  const { build, Platform, Arch } = builderRequire('electron-builder');
  const files = await build({ projectDir: project, targets: Platform.WINDOWS.createTarget(['nsis'], Arch.x64), publish: 'never', config: {
    publish: [], appId: 'com.npdms.desktop.windows-service-candidate', productName: 'NPDMS Windows Service Candidate',
    electronVersion: '45.2.3', asar: false, npmRebuild: false, directories: { output },
    files: ['src/**/*', 'resources/**/*', 'package.json', 'CANDIDATE-NOTICE.txt'],
    artifactName: 'NPDMS-Windows-x64-Installed-DAC-Integration-CANDIDATE.exe',
    win: { target: ['nsis'], signAndEditExecutable: false },
    nsis: { oneClick: false, perMachine: false, allowElevation: false, allowToChangeInstallationDirectory: true,
      runAfterFinish: false, deleteAppDataOnUninstall: false, license: path.join(project, 'CANDIDATE-NOTICE.txt') }
  } });
  console.log(JSON.stringify({ files }));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
