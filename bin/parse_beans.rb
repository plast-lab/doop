#!/bin/ruby
require 'nokogiri'

# This script takes XML files as its arguments and looks for Java Beans and dependency injection patterns in those files. It outputs its findings in BeanClasses.csv, BeanReachable.csv, BeanInit.csv and BeanIoCField.csv.

beans = ARGV.map{|f|
    Nokogiri::XML(File.open(f)).xpath("//beans/bean")
}.flatten

@mm = beans.map{|x| [(x&.attribute('id')&.value or x&.attribute('name')&.value), x]}.select(&:first).to_h

def bean_walk(x,deep)
    h = {}
    h['props'] = {}
    if x == nil then
        return h
    end
    par = x&.attribute('parent')&.value
    if par then
        h = bean_walk(@mm[par],deep)
        if par == 'webscript' then
            h['webscript'] = true
        end
    end
    name = (x&.attribute('id')&.value or x&.attribute('name')&.value)
    h['name'] = name if name
    cls = x&.attribute('class')&.value
    h['cls'] = cls if cls
    init = x&.attribute('init-method')&.value
    h['init'] = init if init
    if deep then
        x.xpath("property").each{|p|
            name = p&.attribute('name')&.value
            ref = p&.attribute('ref')&.value
            other = bean_walk(@mm[ref],false)
            if name and other['cls'] then
                h['props'][name] = other['cls']
            end
        }
    end
    h
end

beans.map!{|b|
    bean_walk(b,true)
}

File.open('BeanClasses.csv', 'w') {|file|
    beans.each{|b| file.write(b['cls']+"\n") if b['cls']}
}

File.open('BeanReachable.csv', 'w') {|file|
    beans.each{|b| file.write(b['cls']+"\texecuteImpl"+"\n") if b['cls'] and b['webscript']}
}

File.open('BeanInit.csv', 'w') {|file|
    beans.each{|b| file.write(b['cls']+"\t"+b['init']+"\n") if b['cls'] and b['init']}
}

File.open('BeanIoCField.csv', 'w') {|file|
    beans.each{|b|
        b['props'].each{|p,r|
            file.write(b['cls']+"\t"+p+"\t"+r+"\n") if b['cls']
        }
    }
}
