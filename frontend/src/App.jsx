import React, { useState, useEffect } from 'react';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';
import './App.css';

function App() {
  // App state management
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [activeInputMode, setActiveInputMode] = useState('upload');
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState(null);
  
  // Tracked and history list states
  const [trackedMedicines, setTrackedMedicines] = useState([]);
  const [toastMessage, setToastMessage] = useState('');

  // Stepper loader states
  const [loading, setLoading] = useState(false);
  const [loadingTitle, setLoadingTitle] = useState('');
  const [loadingStep, setLoadingStep] = useState(null); // 'ocr' | 'verify' | 'scrape' | 'model'

  // Medicine loaded states
  const [activeTab, setActiveTab] = useState('prices');
  const [showResults, setShowResults] = useState(false);
  const [isSymptom, setIsSymptom] = useState(false);

  // Response details state
  const [medicineName, setMedicineName] = useState('');
  const [composition, setComposition] = useState('');
  const [strength, setStrength] = useState('');
  const [manufacturer, setManufacturer] = useState('');
  const [dosageForm, setDosageForm] = useState('');
  const [prices, setPrices] = useState([]);
  const [sideEffects, setSideEffects] = useState(null);
  const [alternatives, setAlternatives] = useState(null);
  const [foodSupport, setFoodSupport] = useState(null);

  // Collapsed states map for alternative sub-grids
  const [expandedAlts, setExpandedAlts] = useState({});

  // Precaution confirmation modal states
  const [showPrecautionModal, setShowPrecautionModal] = useState(false);
  const [pendingRedirectUrl, setPendingRedirectUrl] = useState('');
  const [pendingWebsite, setPendingWebsite] = useState('');

  // Load tracked medicines on mount
  useEffect(() => {
    fetchTrackedMedicines();
  }, []);

  const fetchTrackedMedicines = async () => {
    try {
      const response = await fetch('/api/medicines');
      if (response.ok) {
        const data = await response.json();
        setTrackedMedicines(data || []);
      }
    } catch (e) {
      console.warn('Failed to fetch tracked list:', e);
    }
  };

  const saveMedicine = async () => {
    if (!medicineName) return;
    try {
      const response = await fetch('/api/medicines', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: medicineName })
      });
      if (response.ok) {
        showToast('💾 Saved to medicine tracking list!');
        fetchTrackedMedicines();
      } else {
        showToast('⚠️ Could not save medicine.');
      }
    } catch (e) {
      console.warn(e);
      showToast('⚠️ Error connecting to server.');
    }
  };

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage('');
    }, 3000);
  };

  const getPharmacySearchUrl = (websiteName, medName) => {
    const cleanName = medName || medicineName || searchQuery || '';
    const query = encodeURIComponent(cleanName);
    const site = (websiteName || '').toLowerCase();
    
    if (site.includes('1mg')) {
      return `https://www.1mg.com/search/all?name=${query}`;
    }
    if (site.includes('apollo')) {
      return `https://www.apollopharmacy.in/search-medicines/${query}`;
    }
    if (site.includes('netmeds')) {
      return `https://www.netmeds.com/products/?q=${query}`;
    }
    if (site.includes('pharmeasy')) {
      return `https://pharmeasy.in/search/all?name=${query}`;
    }
    return `https://www.google.com/search?q=${query}+price+online`;
  };

  const generatePdfBlob = async () => {
    // 1. Temporarily show all tab panels
    const panels = document.querySelectorAll('.tab-panel');
    const originalStyles = [];
    panels.forEach(p => {
      originalStyles.push({ element: p, className: p.className });
      p.className = 'tab-panel'; // Remove hidden-on-screen
    });

    // 2. Select the report element
    const reportElement = document.querySelector('.result-screen');
    if (!reportElement) {
      // Restore styles
      originalStyles.forEach(item => { item.element.className = item.className; });
      throw new Error('Report element not found');
    }

    try {
      const canvas = await html2canvas(reportElement, {
        scale: 1.5, // Good balance of quality vs file size
        useCORS: true,
        backgroundColor: '#f8fafc' // Matches theme background
      });

      // Restore styles immediately
      originalStyles.forEach(item => { item.element.className = item.className; });

      const imgData = canvas.toDataURL('image/jpeg', 0.95);
      const pdf = new jsPDF('p', 'mm', 'a4');
      const imgWidth = 210; // A4 width in mm
      const pageHeight = 297; // A4 height in mm
      const imgHeight = (canvas.height * imgWidth) / canvas.width;
      let heightLeft = imgHeight;
      let position = 0;

      // Add first page
      pdf.addImage(imgData, 'JPEG', 0, position, imgWidth, imgHeight);
      heightLeft -= pageHeight;

      // Add extra pages if needed
      while (heightLeft > 0) {
        position = heightLeft - imgHeight;
        pdf.addPage();
        pdf.addImage(imgData, 'JPEG', 0, position, imgWidth, imgHeight);
        heightLeft -= pageHeight;
      }

      return pdf.output('blob');
    } catch (e) {
      // Restore styles in case of failure
      originalStyles.forEach(item => { item.element.className = item.className; });
      throw e;
    }
  };

  const shareReport = async () => {
    showToast('📊 Preparing PDF file to share...');
    try {
      const pdfBlob = await generatePdfBlob();
      const file = new File([pdfBlob], `MedScanX_Report_${medicineName.replace(/\s+/g, '_')}.pdf`, { type: 'application/pdf' });
      
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({
          files: [file],
          title: `MedScanX Report - ${medicineName}`,
          text: `Here is the clinical MedScanX medicine report for ${medicineName}.`
        });
        showToast('✅ Report shared successfully!');
      } else {
        // Fallback to text copy if file sharing is not supported by browser
        const shareText = `MedScanX Report for: ${medicineName}\n` +
          `• Active Composition: ${composition || 'N/A'}\n` +
          `• Best Live Price: ₹${bestPrice || 'N/A'}\n` +
          `• Recommended Food Support: ${foodSupport?.foodSuggestions?.map(f => f.food).join(', ') || 'N/A'}`;
        await navigator.clipboard.writeText(shareText);
        showToast('📋 PDF share not supported. Copied text summary to clipboard!');
      }
    } catch (e) {
      console.warn(e);
      showToast('⚠️ Failed to generate or share PDF.');
    }
  };

  const downloadReportPdf = async () => {
    showToast('📥 Generating PDF file...');
    try {
      const pdfBlob = await generatePdfBlob();
      const url = URL.createObjectURL(pdfBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `MedScanX_Report_${medicineName.replace(/\s+/g, '_')}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
      showToast('✅ PDF Report downloaded successfully!');
    } catch (e) {
      console.warn(e);
      showToast('⚠️ Failed to download PDF report.');
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      setError(null);
      const reader = new FileReader();
      reader.onload = (evt) => {
        setPreviewUrl(evt.target.result);
      };
      reader.readAsDataURL(file);
    }
  };

  const clearImageSelection = () => {
    setSelectedFile(null);
    setPreviewUrl('');
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.currentTarget.classList.add('dragover');
  };

  const handleDragLeave = (e) => {
    e.currentTarget.classList.remove('dragover');
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.currentTarget.classList.remove('dragover');
    if (e.dataTransfer.files.length) {
      const file = e.dataTransfer.files[0];
      setSelectedFile(file);
      setError(null);
      const reader = new FileReader();
      reader.onload = (evt) => {
        setPreviewUrl(evt.target.result);
      };
      reader.readAsDataURL(file);
    }
  };

  const toggleAltStores = (idx) => {
    setExpandedAlts(prev => ({
      ...prev,
      [idx]: !prev[idx]
    }));
  };

  const isSymptomQueryText = (input) => {
    const lower = input.toLowerCase().trim();
    return lower.includes("pain") || lower.includes("cough") || lower.includes("throat") ||
           lower.includes("cold") || lower.includes("constipation") || lower.includes("stomach") ||
           lower.includes("upset") || lower.includes("indigestion") || lower.includes("acid") ||
           lower.includes("reflux") || lower.includes("fever") || lower.includes("headache") ||
           lower.includes("ache") || lower.includes("nausea") || lower.includes("bloating") ||
           lower.includes("diarrhea") || lower.includes("vomit") || lower.includes("motion") ||
           lower.includes("flu") || lower.includes("cramp") || lower.includes("symptom");
  };

  const resetMedicineDetails = () => {
    setMedicineName('');
    setComposition('');
    setStrength('');
    setManufacturer('');
    setDosageForm('');
    setPrices([]);
    setSideEffects(null);
    setAlternatives(null);
    setFoodSupport(null);
  };

  const uploadAndIdentify = async () => {
    resetMedicineDetails();
    if (!selectedFile) {
      setError('Please select a medicine label image first.');
      return;
    }

    setLoading(true);
    setShowResults(false);
    setLoadingTitle('Analyzing clinical packaging label...');
    setLoadingStep('ocr');
    setError(null);

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      await new Promise(r => setTimeout(r, 600));
      setLoadingStep('verify');

      // 1. Identify the brand name from the packaging image
      const identifyResponse = await fetch('/api/medicines/identify', {
        method: 'POST',
        body: formData
      });

      if (!identifyResponse.ok) {
        throw new Error('Could not analyze medicine image. Please ensure packaging is readable.');
      }

      const identifyData = await identifyResponse.json();
      
      if (!identifyData.brandName || identifyData.brandName === 'UNVERIFIED') {
        throw new Error('Could not identify a verified medicine brand from the label packaging.');
      }

      const identifiedBrand = identifyData.brandName;
      setMedicineName(identifiedBrand);

      await new Promise(r => setTimeout(r, 600));
      setLoadingStep('scrape');

      // 2. Check if prices for this medicine are already cached in Redis/DB
      let priceResponse = await fetch('/api/medicines/search?name=' + encodeURIComponent(identifiedBrand));
      let fetchedPrices = [];
      if (priceResponse.ok) {
        fetchedPrices = await priceResponse.json();
      }

      // 3. Cache Miss: Trigger the live concurrent Playwright scraper
      if (!fetchedPrices || fetchedPrices.length === 0) {
        const scrapeResponse = await fetch('/api/medicines/scrape?name=' + encodeURIComponent(identifiedBrand), {
          method: 'POST'
        });
        if (scrapeResponse.ok) {
          fetchedPrices = await scrapeResponse.json();
        }
      }

      setIsSymptom(false);
      setPrices(fetchedPrices.map(p => ({ website: p.website, price: p.price, availability: p.availability })) || []);

      setLoadingStep('model');

      // 4. Fetch safety warnings, substitutes, and diet support concurrently
      await Promise.all([
        fetchSideEffects(identifiedBrand),
        fetchAlternatives(identifiedBrand),
        fetchFoodSupport(identifiedBrand)
      ]);

      setLoading(false);
      setShowResults(true);
      setActiveTab('prices');

    } catch (err) {
      setLoading(false);
      setError(err.message);
    }
  };

  const performTextSearch = async (overrideQuery, forceMode) => {
    resetMedicineDetails();
    const query = (typeof overrideQuery === 'string' ? overrideQuery : searchQuery).trim();
    if (!query) {
      setError('Please type a search query.');
      return;
    }

    setLoading(true);
    setShowResults(false);
    setError(null);

    const mode = forceMode || (typeof overrideQuery === 'string' ? 'medicine' : activeInputMode);
    const symptomCheck = (mode === 'disease');

    if (symptomCheck) {
      setLoadingTitle('Searching food support remedies for: ' + query + '...');
      setLoadingStep('model');
      try {
        setIsSymptom(true);
        setMedicineName(query);
        setComposition('Minor Symptom Query');
        setStrength('');
        setManufacturer('');
        
        await fetchFoodSupport(query, true);

        setLoading(false);
        setShowResults(true);
        setActiveTab('food');
      } catch (e) {
        setLoading(false);
        setError('Failed to fetch food support remedies: ' + e.message);
      }
    } else {
      setLoadingTitle('Fetching details for medicine: ' + query + '...');
      setLoadingStep('scrape');

      try {
        setIsSymptom(false);

        let priceResponse = await fetch('/api/medicines/search?name=' + encodeURIComponent(query));
        let fetchedPrices = [];
        if (priceResponse.ok) {
          fetchedPrices = await priceResponse.json();
        }

        if (!fetchedPrices || fetchedPrices.length === 0) {
          const scrapeResponse = await fetch('/api/medicines/scrape?name=' + encodeURIComponent(query), {
            method: 'POST'
          });
          if (scrapeResponse.ok) {
            fetchedPrices = await scrapeResponse.json();
          }
        }

        setMedicineName(query);
        setPrices(fetchedPrices.map(p => ({ website: p.website, price: p.price, availability: p.availability })) || []);

        setLoadingStep('model');
        await Promise.all([
          fetchSideEffects(query),
          fetchAlternatives(query),
          fetchFoodSupport(query, false)
        ]);

        setLoading(false);
        setShowResults(true);
        setActiveTab('prices');
      } catch (e) {
        setLoading(false);
        setError('Could not find information for medicine: ' + query);
      }
    }
  };

  const fetchSideEffects = async (brandName) => {
    try {
      const response = await fetch('/api/medicine/side-effects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ brandName: brandName })
      });
      if (response.ok) {
        const data = await response.json();
        setSideEffects(data);
        if (data.genericName) setComposition(data.genericName);
        if (data.strength) setStrength(data.strength);
        if (data.manufacturer) setManufacturer(data.manufacturer);
      } else {
        setSideEffects(null);
      }
    } catch (e) {
      console.warn(e);
      setSideEffects(null);
    }
  };

  const fetchAlternatives = async (brandName) => {
    try {
      const response = await fetch(`/api/medicines/${encodeURIComponent(brandName)}/alternatives`);
      if (response.ok) {
        const data = await response.json();
        setAlternatives(data);
        if (data.dosageForm) setDosageForm(data.dosageForm);
      } else {
        setAlternatives(null);
      }
    } catch (e) {
      console.warn(e);
      setAlternatives(null);
    }
  };

  const fetchFoodSupport = async (brandName, forceSymptom) => {
    try {
      const url = `/api/medicines/${encodeURIComponent(brandName)}/food-support` + 
                  (forceSymptom !== undefined ? `?symptom=${forceSymptom}` : '');
      const response = await fetch(url);
      if (response.ok) {
        const data = await response.json();
        setFoodSupport(data);
      } else {
        setFoodSupport(null);
      }
    } catch (e) {
      console.warn(e);
      setFoodSupport(null);
    }
  };

  const displayName = selectedFile && selectedFile.name ? (selectedFile.name.length > 30 ? selectedFile.name.substring(0, 27) + '...' : selectedFile.name) : 'image.png';

  const pricesExist = prices && prices.length > 0;
  const validPrices = prices.map(p => p.price).filter(price => price && price > 0);
  const bestPrice = validPrices.length ? Math.min(...validPrices) : 0;
  const highestPrice = validPrices.length ? Math.max(...validPrices) : 0;
  const maxSavings = (highestPrice - bestPrice).toFixed(2);
  const isAlreadySaved = trackedMedicines.some(m => m.name?.toLowerCase() === medicineName?.toLowerCase());

  return (
    <div className="container">
      {/* Toast Notification element */}
      {toastMessage && (
        <div className="toast-msg">
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Medical Navigation bar */}
      <nav>
        <div className="logo">
          <svg viewBox="0 0 24 24">
            <path d="M19 10.5h-5.5V5c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v5.5H5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5h5.5V19c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-5.5H19c.83 0 1.5-.67 1.5-1.5s-.67-1.5-1.5-1.5z" fill="currentColor"/>
          </svg>
          <span>MedScanX</span>
        </div>
      </nav>

      {/* Clinic Error Alert Banner */}
      {error && (
        <div className="error-alert">
          <svg viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <div className="error-alert-content">{error}</div>
        </div>
      )}

      {/* Hero Headline */}
      <div className="hero">
        <h1>MedScanX <span>Medicine Scan &amp; Save</span></h1>
        <p>Upload a medicine packaging label or type a query to dynamically scan pharmacy prices, learn safe side-effects, find same-composition options, and get supportive home remedies.</p>
      </div>

      {/* Clinical Mode Tabs */}
      <div className="mode-tabs">
        <button 
          className={`mode-tab ${activeInputMode === 'upload' ? 'active' : ''}`}
          onClick={() => { setActiveInputMode('upload'); setError(null); setShowResults(false); }}
        >
          📷 Scan Packaging Label
        </button>
        <button 
          className={`mode-tab ${activeInputMode === 'medicine' ? 'active' : ''}`}
          onClick={() => { setActiveInputMode('medicine'); setError(null); setShowResults(false); }}
        >
          💊 Search Medicine by Name
        </button>
        <button 
          className={`mode-tab ${activeInputMode === 'disease' ? 'active' : ''}`}
          onClick={() => { setActiveInputMode('disease'); setError(null); setShowResults(false); }}
        >
          🩺 Search Disease / Symptom
        </button>
      </div>

      {/* Input Panel Card */}
      {!loading && !showResults && (
        <div className="input-card">
          {activeInputMode === 'upload' ? (
            <div>
              <div 
                className="dropzone"
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={() => document.getElementById('fileInput').click()}
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0l3 3m-3-3l-3 3M6.75 19.5a4.5 4.5 0 01-1.41-8.775 5.25 5.25 0 0110.233-2.33 3 3 0 013.758 3.848A3.752 3.752 0 0118 19.5H6.75z" />
                </svg>
                <div>
                  <p>Click or drag medicine pack photo here</p>
                  <span>Accepts JPG, PNG formats</span>
                </div>
              </div>
              <input 
                type="file" 
                id="fileInput" 
                accept="image/*" 
                style={{ display: 'none' }} 
                onChange={handleFileSelect}
              />
              
              {previewUrl && (
                <div className="preview-box">
                  <div className="preview-header">
                    <span>{displayName}</span>
                    <div className="preview-actions">
                      <button onClick={clearImageSelection}>Remove</button>
                    </div>
                  </div>
                  <div className="preview-img-container">
                    <img src={previewUrl} alt="Preview" />
                  </div>
                </div>
              )}

              <button 
                className="btn" 
                style={{ width: '100%', marginTop: '1.5rem' }}
                onClick={uploadAndIdentify}
                disabled={!selectedFile}
              >
                🩺 Analyze Medicine Details
              </button>
            </div>
          ) : activeInputMode === 'disease' ? (
            <div className="search-bar">
              <input 
                type="text" 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Type a disease or symptom (e.g. headache, fever, cold)..."
                onKeyDown={(e) => { if (e.key === 'Enter') performTextSearch(null, 'disease'); }}
              />
              <button className="btn" onClick={() => performTextSearch(null, 'disease')}>
                Search Disease
              </button>
            </div>
          ) : (
            <div className="search-bar">
              <input 
                type="text" 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Type a medicine name (e.g. Dolo 650, Calpol)..."
                onKeyDown={(e) => { if (e.key === 'Enter') performTextSearch(null, 'medicine'); }}
              />
              <button className="btn" onClick={() => performTextSearch(null, 'medicine')}>
                Search Medicine
              </button>
            </div>
          )}
        </div>
      )}

      {/* Saved & Scanned History Panel */}
      {!loading && !showResults && trackedMedicines.length > 0 && (
        <div className="history-card">
          <h3>📋 Saved &amp; Tracked Medicines</h3>
          <div className="history-grid">
            {trackedMedicines.map((med, i) => (
              <button 
                key={i} 
                className="history-tag"
                onClick={() => {
                  setSearchQuery(med.name);
                  performTextSearch(med.name);
                }}
              >
                {med.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Stepper Analysis Loader */}
      {loading && (
        <div className="input-card">
          <div className="analysis-loader">
            <div className="pulse-scanner">
              <svg viewBox="0 0 24 24" fill="none">
                <rect x="2" y="2" width="20" height="20" rx="4" />
                <path d="M7 12h10M12 7v10" />
              </svg>
              <div className="scanner-line"></div>
            </div>
            <div className="loader-title">{loadingTitle}</div>
            <div className="loading-steps">
              <div className={`step-item ${loadingStep === 'ocr' ? 'active' : ''} ${['verify', 'scrape', 'model'].includes(loadingStep) ? 'done' : ''}`}>
                <div className="step-circle">{['verify', 'scrape', 'model'].includes(loadingStep) ? '✓' : '1'}</div>
                <span>Reading medicine label texts (OCR)</span>
              </div>
              <div className={`step-item ${loadingStep === 'verify' ? 'active' : ''} ${['scrape', 'model'].includes(loadingStep) ? 'done' : ''}`}>
                <div className="step-circle">{['scrape', 'model'].includes(loadingStep) ? '✓' : '2'}</div>
                <span>Verifying composition &amp; safety standards</span>
              </div>
              <div className={`step-item ${loadingStep === 'scrape' ? 'active' : ''} ${loadingStep === 'model' ? 'done' : ''}`}>
                <div className="step-circle">{loadingStep === 'model' ? '✓' : '3'}</div>
                <span>Comparing live market store prices concurrently</span>
              </div>
              <div className={`step-item ${loadingStep === 'model' ? 'active' : ''}`}>
                <div className="step-circle">4</div>
                <span>Structuring clinical safety &amp; alternative remedies</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Initial State Placeholder */}
      {!loading && !showResults && (
        <div className="empty-state">
          <svg viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <h3>No Active Analysis</h3>
          <p>Upload a packaging label photo or perform a search query. The clinical report insights will populate here.</p>
        </div>
      )}

      {/* Clinical Result Screen */}
      {!loading && showResults && (
        <div className="result-screen">
          {/* Sticky Header context card */}
          <div className="context-card">
            <div className="context-info">
              <div className="context-icon">
                <svg viewBox="0 0 24 24" stroke="currentColor">
                  <rect x="2" y="7" width="20" height="14" rx="2" />
                  <path d="M6 7V5a3 3 0 016 0v2m0-2h6a3 3 0 013 3v2" />
                </svg>
              </div>
              <div className="context-detail">
                <h2>{medicineName} {isSymptom && <span className={`pill-badge ${foodSupport?.chronicCondition ? 'warning' : ''}`}>{foodSupport?.chronicCondition ? 'Doctor Supervision Required' : 'Symptom Care'}</span>}</h2>
                <p>{isSymptom ? (foodSupport?.chronicCondition ? 'Chronic / Severe Condition Care' : 'Minor Symptom Search') : `${composition} ${strength ? '• ' + strength : ''} • ${dosageForm || 'Tablet'}`}</p>
              </div>
            </div>
            {!isSymptom && manufacturer && (
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', textAlign: 'right' }}>
                Manufactured by<br />
                <strong style={{ color: 'var(--text-primary)', fontSize: '0.95rem' }}>{manufacturer}</strong>
              </div>
            )}

            {/* Save, Print, Share Action Buttons Layout */}
            <div className="action-btn-group" style={{ width: '100%' }}>
              {!isSymptom && (
                <button 
                  className={`action-btn ${isAlreadySaved ? 'saved' : ''}`}
                  onClick={isAlreadySaved ? null : saveMedicine}
                  disabled={isAlreadySaved}
                >
                  {isAlreadySaved ? '✓ Saved to Tracked List' : '💾 Save to Tracked List'}
                </button>
              )}
              <button className="action-btn" onClick={downloadReportPdf}>
                📥 Download PDF Report
              </button>
              <button className="action-btn" onClick={shareReport}>
                🔗 Share Report
              </button>
            </div>
          </div>

          {/* Horizontal Tabs */}
          <div className="result-nav">
            {!isSymptom && (
              <button 
                className={`nav-item ${activeTab === 'prices' ? 'active' : ''}`}
                onClick={() => setActiveTab('prices')}
              >
                <svg viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18.75a60.07 60.07 0 0115.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5h.007m-.007 3h.007m-.007 3h.007m3-6h12a1.5 1.5 0 011.5 1.5v15a1.5 1.5 0 01-1.5 1.5h-12a1.5 1.5 0 01-1.5-1.5v-15a1.5 1.5 0 011.5-1.5z" />
                </svg>
                Price Comparison
              </button>
            )}
            {!isSymptom && (
              <button 
                className={`nav-item ${activeTab === 'safety' ? 'active' : ''}`}
                onClick={() => setActiveTab('safety')}
              >
                <svg viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
                </svg>
                Side Effects &amp; Safety
              </button>
            )}
            {!isSymptom && (
              <button 
                className={`nav-item ${activeTab === 'alternatives' ? 'active' : ''}`}
                onClick={() => setActiveTab('alternatives')}
              >
                <svg viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                </svg>
                Substitute Options
              </button>
            )}
            <button 
              className={`nav-item ${activeTab === 'food' ? 'active' : ''}`}
              onClick={() => setActiveTab('food')}
            >
              <svg viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
              </svg>
              Dietary Support
            </button>
          </div>

          {/* Tab 1: Prices Grid */}
          {!isSymptom && (
            <div className={`tab-panel ${activeTab === 'prices' ? '' : 'hidden-on-screen'}`}>
              <div className="precaution-banner" style={{ marginBottom: '1.5rem', backgroundColor: '#eff6ff', borderLeft: '4px solid #3b82f6' }}>
                <div className="precaution-banner-title" style={{ color: '#1d4ed8' }}>ℹ️ Verify Scanned Medicine</div>
                <p style={{ fontSize: '0.85rem', color: '#1e3a8a', lineHeight: '1.4', margin: 0 }}>
                  Click the store price card below to confirm if this is the medicine you scanned or searched for.
                </p>
              </div>
              <div className="price-overview">
                <div className="price-grid">
                  {pricesExist ? (
                    prices.map((p, idx) => {
                      const isCheapest = p.price === bestPrice && p.price > 0;
                      const isOut = !p.price || p.price <= 0 || (p.availability && (
                        p.availability.toLowerCase().includes('out') ||
                        p.availability.toLowerCase().includes('not_found') ||
                        p.availability.toLowerCase().includes('unavailable') ||
                        p.availability.toLowerCase().includes('fail')
                      ));
                      const displayAvailability = isOut ? 'UNAVAILABLE' : 'AVAILABLE';
                      const searchUrl = getPharmacySearchUrl(p.website, p.medicineName || medicineName);
                      return (
                        <a 
                          key={idx} 
                          href={searchUrl} 
                          onClick={(e) => {
                            e.preventDefault();
                            setPendingRedirectUrl(searchUrl);
                            setPendingWebsite(p.website);
                            setShowPrecautionModal(true);
                          }}
                          target="_blank" 
                          rel="noopener noreferrer" 
                          className="pharmacy-card-link"
                          style={{ textDecoration: 'none', color: 'inherit', display: 'block' }}
                        >
                          <div className={`pharmacy-card ${isCheapest ? 'cheapest' : ''}`}>
                            {isCheapest && <div className="best-price-ribbon">Best price</div>}
                            <div className="pharmacy-header">
                              <div className="pharmacy-logo-placeholder">{p.website ? p.website.charAt(0) : 'P'}</div>
                              <span className="pharmacy-name">{p.website || 'Pharmacy'}</span>
                            </div>
                            <div className="pharmacy-med-title">{p.medicineName || medicineName}</div>
                            <div className="pharmacy-price-row">
                              <span className="pharmacy-price">{p.price && p.price > 0 ? `₹${p.price}` : 'N/A'}</span>
                              <span className={`availability-pill ${isOut ? 'out' : ''}`}>{displayAvailability}</span>
                            </div>
                          </div>
                        </a>
                      );
                    })
                  ) : (
                    <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '3rem 1rem', color: 'var(--text-muted)' }}>
                      No dynamic store prices available for this brand currently.
                    </div>
                  )}
                </div>
                {pricesExist && (
                  <div className="insights-card">
                    <div className="insights-title">
                      <svg viewBox="0 0 24 24" style={{ width: '20px', height: '20px' }} fill="none" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2m0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                      </svg>
                      <span>Price Insights</span>
                    </div>
                    <div className="insight-stat">
                      <span className="label">Best Price Found</span>
                      <span className="value" style={{ color: 'var(--accent-emerald)' }}>₹{bestPrice}</span>
                    </div>
                    <div className="insight-stat">
                      <span className="label">Highest Market Price</span>
                      <span className="value">₹{highestPrice}</span>
                    </div>
                    <div className="insight-stat saving-stat">
                      <span className="label">Maximum Savings</span>
                      <span className="value">₹{maxSavings}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Tab 2: Side Effects */}
          {!isSymptom && (
            <div className={`tab-panel ${activeTab === 'safety' ? '' : 'hidden-on-screen'}`}>
              {sideEffects ? (
                <div className="side-effects-layout">
                  <div className="side-effects-col">
                    {sideEffects.whatItDoes && (
                      <div className="info-card">
                        <div className="info-card-title">💊 What it does</div>
                        <p className="info-card-text">{sideEffects.whatItDoes}</p>
                      </div>
                    )}
                    {sideEffects.avoidIf && (
                      <div className="info-card">
                        <div className="info-card-title" style={{ color: 'var(--accent-orange)' }}>⚠️ Avoid this if</div>
                        <p className="info-card-text">{sideEffects.avoidIf}</p>
                      </div>
                    )}
                  </div>
                  <div className="side-effects-col">
                    {sideEffects.commonSideEffects && sideEffects.commonSideEffects.length > 0 && (
                      <div className="info-card">
                        <div className="info-card-title">🔹 Common side effects</div>
                        <ul className="bullet-list">
                          {sideEffects.commonSideEffects.map((item, i) => <li key={i}>{item}</li>)}
                        </ul>
                      </div>
                    )}
                    {sideEffects.lessCommonSideEffects && sideEffects.lessCommonSideEffects.length > 0 && (
                      <div className="info-card">
                        <div className="info-card-title">🔹 Less common side effects</div>
                        <ul className="bullet-list">
                          {sideEffects.lessCommonSideEffects.map((item, i) => <li key={i}>{item}</li>)}
                        </ul>
                      </div>
                    )}
                    {sideEffects.seriousWarnings && sideEffects.seriousWarnings.length > 0 && (
                      <div className="warning-banner">
                        <div className="warning-banner-title">🚨 Seek urgent medical help if you get:</div>
                        <ul className="warning-banner">
                          {sideEffects.seriousWarnings.map((item, i) => <li key={i}>{item}</li>)}
                        </ul>
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--text-muted)' }}>
                  Safety guidelines are currently unavailable for this brand.
                </div>
              )}
              {sideEffects && sideEffects.disclaimer && (
                <div className="disclaimer-banner">{sideEffects.disclaimer}</div>
              )}
            </div>
          )}

          {/* Tab 3: Alternatives Substitute Options */}
          {!isSymptom && (
            <div className={`tab-panel ${activeTab === 'alternatives' ? '' : 'hidden-on-screen'}`}>
              {alternatives && alternatives.disclaimer && (
                <div className="precaution-banner" style={{ marginBottom: '2rem' }}>
                  <div className="precaution-banner-title" style={{ color: 'var(--accent-indigo)' }}>⚕️ Precaution notice</div>
                  <p style={{ fontSize: '0.85rem', color: '#334155', lineHeight: '1.4' }}>{alternatives.disclaimer}</p>
                </div>
              )}
              {alternatives && alternatives.alternatives && alternatives.alternatives.length > 0 ? (
                <div className="alternatives-container">
                  {alternatives.alternatives.map((alt, idx) => {
                    const isExpanded = expandedAlts[idx];
                    return (
                      <div key={idx} className="alternative-card">
                        <div className="alternative-header">
                          <div className="alternative-title">
                            <h3>{alt.medicineName}</h3>
                            <p>{alt.composition || alternatives.composition} • {alt.strength || alternatives.strength} • {alt.dosageForm || alternatives.dosageForm || 'Tablet'}</p>
                          </div>
                          <div className="alternative-price-box">
                            <span className="label">Starting Price</span>
                            <div className="price">₹{alt.lowestPrice || 'N/A'}</div>
                          </div>
                        </div>
                        <button 
                          className="alt-stores-trigger"
                          onClick={() => toggleAltStores(idx)}
                        >
                          <span>Compare store prices</span>
                          <svg 
                            viewBox="0 0 24 24" 
                            style={{ 
                              width: '14px', 
                              height: '14px', 
                              fill: 'none', 
                              stroke: 'currentColor', 
                              strokeWidth: '2.5',
                              transform: isExpanded ? 'rotate(180deg)' : 'none',
                              transition: 'transform 0.2s'
                            }}
                          >
                            <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
                          </svg>
                        </button>
                        
                        {isExpanded && (
                          <div className="alt-store-panel">
                            <div className="alt-stores-grid">
                              {alt.storePrices && alt.storePrices.length > 0 ? (
                                alt.storePrices.map((sp, i) => {
                                  const isOut = !sp.price || sp.price <= 0 || (sp.availability && (
                                    sp.availability.toLowerCase().includes('out') ||
                                    sp.availability.toLowerCase().includes('not') ||
                                    sp.availability.toLowerCase().includes('unavail') ||
                                    sp.availability.toLowerCase().includes('fail')
                                  ));
                                  return (
                                    <div key={i} className="alt-store-row">
                                      <span className="name">{sp.website}</span>
                                      <span className="price">
                                        {!isOut && sp.price ? `₹${sp.price}` : 'N/A'}{' '}
                                        <span 
                                          style={{ 
                                            fontSize: '0.65rem', 
                                            fontWeight: '600',
                                            padding: '2px 6px',
                                            borderRadius: '4px',
                                            marginLeft: '4px',
                                            backgroundColor: isOut ? '#fef2f2' : '#f0fdf4',
                                            color: isOut ? '#ef4444' : '#16a34a'
                                          }}
                                        >
                                          {isOut ? 'UNAVAILABLE' : (sp.availability || 'AVAILABLE').toUpperCase()}
                                        </span>
                                      </span>
                                    </div>
                                  );
                                })
                              ) : (
                                <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>No store prices scraped.</div>
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--text-muted)' }}>
                  No same-composition alternative options matching this formula were found.
                </div>
              )}
            </div>
          )}

          {/* Tab 4: Food Support Grid */}
          <div className={`tab-panel ${activeTab === 'food' ? '' : 'hidden-on-screen'}`}>
              {foodSupport && foodSupport.foodSupportAvailable && foodSupport.foodSuggestions && foodSupport.foodSuggestions.length > 0 ? (
                <div className="food-grid">
                  {foodSupport.foodSuggestions.map((item, idx) => (
                    <div key={idx} className="food-card">
                      <div className="food-card-header">
                        <div className="food-card-icon">🥣</div>
                        <span className="food-card-title">{item.food}</span>
                      </div>
                      <div className="food-detail-item">
                        <div className="label">Suggested Quantity</div>
                        <div className="value">{item.quantity || 'Standard serving'}</div>
                      </div>
                      <div className="food-detail-item">
                        <div className="label">Helpful Part</div>
                        <div className="value" style={{ color: 'var(--accent-teal)', fontWeight: '600' }}>{item.helpfulPart || 'Natural supportive element'}</div>
                      </div>
                      <div className="food-detail-item">
                        <div className="label">Why it may help</div>
                        <div className="value">{item.reason}</div>
                      </div>
                      {item.precaution && (
                        <div className="food-precaution">
                          <strong>💡 Note:</strong> {item.precaution}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--text-muted)' }}>
                  No food-based supportive remedies are appropriate for this condition. Please follow prescribed instructions.
                </div>
              )}
              {foodSupport && foodSupport.warning && (
                <div className="precaution-banner" style={{ marginTop: '2rem', background: foodSupport.chronicCondition ? '#fff1f2' : '#f0fdf4', borderColor: foodSupport.chronicCondition ? '#fda4af' : '#bbf7d0' }}>
                  <div className="precaution-banner-title" style={{ color: foodSupport.chronicCondition ? '#e11d48' : 'var(--accent-teal)', fontSize: '1rem', fontWeight: '700' }}>
                    {foodSupport.chronicCondition ? '🚨 CRITICAL MEDICAL ADVISORY' : '🥦 Food supportive care notice'}
                  </div>
                  <p style={{ fontSize: '0.9rem', color: foodSupport.chronicCondition ? '#9f1239' : '#334155', lineHeight: '1.5', fontWeight: foodSupport.chronicCondition ? '600' : '400' }}>
                    {foodSupport.warning}
                  </p>
                </div>
              )}
            </div>
          
          {/* Option to clear results and start again */}
          <div style={{ textAlign: 'center', marginTop: '2.5rem' }}>
            <button className="btn" style={{ background: '#ffffff', border: '1.5px solid var(--panel-border)', color: 'var(--text-primary)' }} onClick={() => { setShowResults(false); clearImageSelection(); setSearchQuery(''); }}>
              ← Scan / Search another query
            </button>
          </div>
        </div>
      )}

      {showPrecautionModal && (
        <div className="modal-backdrop" style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.65)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 9999,
          padding: '1rem'
        }}>
          <div className="modal-content" style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '2rem',
            maxWidth: '500px',
            width: '100%',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
            border: '1px solid #e2e8f0',
            animation: 'fadeIn 0.25s ease-out'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '1.25rem' }}>
              <div style={{
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                backgroundColor: '#fef2f2',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                color: '#ef4444'
              }}>
                <svg viewBox="0 0 24 24" style={{ width: '24px', height: '24px', fill: 'none', stroke: 'currentColor', strokeWidth: '2' }}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', color: '#0f172a', margin: 0 }}>
                Confirm Medicine Selection
              </h2>
            </div>
            
            <p style={{ fontSize: '0.95rem', color: '#475569', lineHeight: '1.5', marginBottom: '1rem' }}>
              You are about to visit <strong>{pendingWebsite}</strong> to view/purchase:
            </p>
            
            <div style={{
              backgroundColor: '#f8fafc',
              borderRadius: '8px',
              padding: '1rem',
              border: '1px solid #e2e8f0',
              marginBottom: '1.5rem'
            }}>
              <div style={{ fontWeight: '700', color: '#1e293b', fontSize: '1.05rem', marginBottom: '4px' }}>
                {medicineName || searchQuery}
              </div>
              <div style={{ fontSize: '0.85rem', color: '#64748b' }}>
                {composition && composition !== 'Unspecified Composition' ? composition : 'Check package label composition'}
              </div>
            </div>

            <div style={{
              borderLeft: '4px solid #f59e0b',
              backgroundColor: '#fffbeb',
              padding: '0.75rem 1rem',
              borderRadius: '0 8px 8px 0',
              marginBottom: '2rem'
            }}>
              <div style={{ fontWeight: '700', color: '#b45309', fontSize: '0.85rem', marginBottom: '4px' }}>
                ⚠️ IMPORTANT SAFETY PRECAUTIONS:
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.825rem', color: '#78350f', lineHeight: '1.4' }}>
                <li>Confirm this is the exact medicine brand & strength prescribed by your doctor.</li>
                <li>Different manufacturers may use different inactive ingredients.</li>
                <li>Check for "prescription required" tags on the store page before placing order.</li>
              </ul>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button 
                className="btn btn-secondary" 
                onClick={() => setShowPrecautionModal(false)}
                style={{
                  padding: '0.625rem 1.25rem',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#ffffff',
                  color: '#475569',
                  fontWeight: '600',
                  fontSize: '0.9rem',
                  cursor: 'pointer'
                }}
              >
                Cancel
              </button>
              <button 
                className="btn" 
                onClick={() => {
                  window.open(pendingRedirectUrl, '_blank', 'noopener,noreferrer');
                  setShowPrecautionModal(false);
                }}
                style={{
                  padding: '0.625rem 1.25rem',
                  borderRadius: '8px',
                  backgroundColor: 'var(--accent-blue, #0284c7)',
                  color: '#ffffff',
                  border: 'none',
                  fontWeight: '600',
                  fontSize: '0.9rem',
                  cursor: 'pointer'
                }}
              >
                Yes, Proceed to Store
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
